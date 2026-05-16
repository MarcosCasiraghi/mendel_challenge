# Mendel Challenge — Transactions Service

The goal of this challenge is to develop a REST service that stores financial transactions, exposes them by type, and computes the transitive sum of amounts across parent/child transactions.

This document captures the architectural choices and the API endpoints exposed.

## Tech Stack

- Java 17
- Spring Boot 4.0.6 (`spring-boot-starter-webmvc`, `spring-boot-starter-validation`)
- Lombok for boilerplate reduction
- Maven as build tool

No external database is used. Storage is fully in-memory, as mentioned in the description of the challenge.

## Architecture & Design Decisions

### Package-by-feature layout

The codebase is grouped by domain feature rather than by technical layer. Everything related to transactions lives under a single `transaction` package, with sub-packages for DTOs and exceptions. A separate `common` package holds cross-cutting concerns such as the global exception handler.

```
org.example.mendel_challenge
├── MendelChallengeApplication.java
├── common
│   └── GlobalExceptionHandler.java
└── transaction
    ├── api
    │   └──TransactionController.java
    ├── domain
    │   ├──Transaction.java
    ├── dto
    │   ├──StatusResponse.java
    │   ├──SumResponse.java
    │   └──TransactionRequestDTO.java
    ├── exceptions
    │   ├──TransactionAlreadyExistsException.java
    │   └──TransactionNotFoundException.java
    ├── repository
    │   ├──InMemoryTransactionRepository.java
    │   └──TransactionRepository.java
    ├── service
        └──TransactionService.java
```


### Repository as an interface (Dependency Inversion)

`TransactionRepository` is declared as an interface. The current implementation, `InMemoryTransactionRepository`, holds data in a `ConcurrentHashMap`. The service depends on the interface, not on the implementation. This makes it possible to swap the storage layer later (for example, an implementation against PostgreSQL) without touching the service or the controller.

### Thread safety and atomic insertion

The in-memory store relies on `ConcurrentHashMap` and performs writes through `Map#putIfAbsent`, which checks and inserts atomically. This prevents a race condition in which two concurrent requests with the same id could both pass a separate existence check and both insert. The repository returns an `Optional<Transaction>` from `saveIfAbsent` so callers can distinguish "newly inserted" from "already existed".

### Indexed lookups by type

Listing transactions by type is served from a secondary index (`Map<String, Set<Long>>`) maintained inside the repository on every successful insert. This turns `GET /transactions/types/{type}` into an O(1) map lookup instead of an O(n) scan of every stored transaction, at the cost of a small write-time bookkeeping step. The index is only updated when `putIfAbsent` actually accepts the transaction, so duplicate writes never pollute it.

### Transitive sum traversal

The sum endpoint needs to aggregate amounts across every transaction reachable through `parent_id` from a given root. To avoid scanning the full store on each request, a second secondary index `Map<Long, Set<Long>>` keyed by parent id, is maintained at insert time. Each entry maps a parent id to the set of its direct children. Both inner sets use `ConcurrentHashMap.newKeySet()` so concurrent inserts under the same parent are safe.

`getTransactionsSum` then runs a breadth-first walk down this index starting from the requested id, summing `amount` as it visits each reachable transaction. A `visited` set guards the BFS against any accidental cycle in the parent_id graph: the model does not technically prevent a client from PUTting `A parent_id=B` together with `B parent_id=A`, so the traversal is written defensively rather than trusting the input.

The endpoint throws a `TransactionDoesNotExistException` if the `transactionId` is not present in the repository store.

### DTOs separated from the domain

The `Transaction` domain class is never exposed directly through the HTTP API. Inbound payloads bind to `TransactionRequest`, and the API returns a `StatusResponse` record. This decouples the wire format from the internal model, lets each side evolve independently, and keeps validation annotations off the domain class.

`StatusResponse` and `SumResponse` are both implemented as Java `record`s, which provide immutability and a clean serialization contract without Lombok.

### snake_case in the wire format, camelCase in the code

The challenge specifies snake_case for both path segments (`transaction_id`) and JSON fields (`parent_id`), but Java code follows camelCase. The mapping is declared explicitly where each field is bound:

- Path variable: `@PathVariable("transaction_id") long transactionId`
- JSON field: `@JsonProperty("parent_id") private Long parentId`

### Centralized error handling

Exception handlers live in a single `@RestControllerAdvice` class (`GlobalExceptionHandler`) rather than on the controller. This keeps controllers focused on request/response orchestration and guarantees consistent error responses as more endpoints are added. The advice currently handles `TransactionAlreadyExistsException` (mapped to `409 Conflict`), `TransactionDoesNotExistException` (mapped to `404 Not Found`), `MethodArgumentNotValidException` raised by Bean Validation (mapped to `400 Bad Request` with a field-level error map), and `HttpMessageNotReadableException` for malformed JSON bodies (mapped to `400 Bad Request`).

### Input validation

Request bodies carry Bean Validation constraints (`@NotNull`, `@NotBlank`) and are validated by annotating the controller parameter with `@Valid`. Validation failures surface as `400` responses through the global exception handler.

### Constructor injection only

All Spring-managed beans use constructor injection via Lombok's `@RequiredArgsConstructor`. There is no field injection. Dependencies are explicit, fields are `final`, and beans remain trivially testable in isolation without Spring.

## API

### `PUT /transactions/{transaction_id}`

Creates a transaction with the given id.

Request body:

```json
{
  "amount": 5000,
  "type": "cars",
  "parent_id": 10
}
```

| Field       | Type   | Required | Description                              |
|-------------|--------|----------|------------------------------------------|
| `amount`    | number | yes      | Transaction amount                       |
| `type`      | string | yes      | Category / classification of the tx      |
| `parent_id` | number | no       | Id of the parent transaction, if any     |

Responses:

| Status            | Body                    | Meaning                                       |
|-------------------|-------------------------|-----------------------------------------------|
| `201 Created`     | `{ "status": "ok" }`    | Transaction was stored                        |
| `409 Conflict`    | error message           | A transaction with the same id already exists |
| `400 Bad Request` | field-error map or text | Validation failed or the body was malformed   |

### `GET /transactions/types/{type}`

Returns the ids of every transaction stored under the given type.

Response body is a JSON array of transaction ids:

```json
[10, 11, 12]
```

| Status   | Body            | Meaning                                                                |
|----------|-----------------|------------------------------------------------------------------------|
| `200 OK` | `[long, ...]`   | List of matching ids (empty array if no transaction has that type)     |

The type segment is treated as an exact, case-sensitive match. An unknown type returns `200 OK` with `[]` rather than `404`, which keeps the contract uniform for callers that just want to enumerate ids.

### `GET /transactions/sum/{transaction_id}`

Returns the transitive sum of `amount` across every transaction reachable through `parent_id` from the given root, including the root itself.

Response body:

```json
{ "sum": 20000.0 }
```

| Status            | Body                | Meaning                                                |
|-------------------|---------------------|--------------------------------------------------------|
| `200 OK`          | `{ "sum": <double> }` | Aggregated amount for the subtree rooted at the id    |
| `404 Not Found`   | error message       | No transaction with that id is stored                  |

Unlike the by-type endpoint, an unknown id here returns `404` rather than `200` with a zero. The behaviour is centralised in the service layer (which performs the existence check) and surfaced through `TransactionDoesNotExistException` in the global handler.

The amount serialises as a JSON number with a fractional part (e.g. `20000.0`), since the field is typed as `double`. This is numerically equivalent to the spec's `{"sum":20000}` example.

## Testing

The project ships with two layers of tests:

- **Unit tests** — `TransactionServiceTest` mocks the repository with Mockito to validate the service behaviour in isolation: save success, the duplicate-id failure path, delegation of `getTransactionsByType` (both populated and empty cases), the sum happy path, and the assertion that a missing root id produces `TransactionDoesNotExistException` without ever calling `getTransactionsSum` on the repository. `InMemoryTransactionRepositoryTest` exercises the repository directly with plain JUnit: insertion semantics, duplicate-id handling, type-index correctness and case sensitivity, `getTransactionById` round-trip, the spec's three-node `10 → 11 → 12` sum example, a wider multi-branch subtree, leaf sums, the repository-level "missing root returns 0" contract, and a synthetic parent_id cycle that proves the BFS terminates (asserted with `assertTimeoutPreemptively`).
- **Integration tests** — `TransactionControllerIntegrationTest` uses `@SpringBootTest` + `MockMvc` to exercise all three endpoints end-to-end. `PUT /transactions/{transaction_id}` is covered for the happy path, duplicate id, missing required fields, optional `parent_id`, snake_case mapping, and malformed JSON. `GET /transactions/types/{type}` is covered for the multi-id happy path, the empty-result case, and type isolation across two coexisting types. `GET /transactions/sum/{transaction_id}` is covered for a spec-shaped three-node tree (asserting the sum at root, middle, and leaf), a node with no children, and the `404` response on an unknown id. Because the in-memory repository bean is shared across the Spring context, tests draw ids and types from atomic counters to stay isolated from each other.

Run the full suite with:

```bash
./mvnw test
```

## Docker

The service ships with a multi-stage `Dockerfile`. Build and run the production image with:

```bash
docker build -t mendel-challenge:latest .
```
```bash
docker run --rm -p 8080:8080 mendel-challenge:latest
```

Run the test suite inside a container, without needing Maven or a JDK on the host:

```bash
docker build --target test --progress=plain .
```

The full rationale for the multi-stage layout, the JDK-vs-JRE split, the non-root user, and why there is no separate `Dockerfile.test` is documented in [DOCKER.md](./DOCKER.md).


