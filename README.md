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
    │   └──TransactionRequestDTO.java
    ├── exceptions
    │   └──TransactionAlreadyExistsException.java
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

### DTOs separated from the domain

The `Transaction` domain class is never exposed directly through the HTTP API. Inbound payloads bind to `TransactionRequest`, and the API returns a `StatusResponse` record. This decouples the wire format from the internal model, lets each side evolve independently, and keeps validation annotations off the domain class.

`StatusResponse` is implemented as a Java `record`, which provides immutability and a clean serialization contract without Lombok.

### snake_case in the wire format, camelCase in the code

The challenge specifies snake_case for both path segments (`transaction_id`) and JSON fields (`parent_id`), but Java code follows camelCase. The mapping is declared explicitly where each field is bound:

- Path variable: `@PathVariable("transaction_id") long transactionId`
- JSON field: `@JsonProperty("parent_id") private Long parentId`

### Centralized error handling

Exception handlers live in a single `@RestControllerAdvice` class (`GlobalExceptionHandler`) rather than on the controller. This keeps controllers focused on request/response orchestration and guarantees consistent error responses as more endpoints are added. The advice currently handles `TransactionAlreadyExistsException` (mapped to `409 Conflict`), `MethodArgumentNotValidException` raised by Bean Validation (mapped to `400 Bad Request` with a field-level error map), and `HttpMessageNotReadableException` for malformed JSON bodies (mapped to `400 Bad Request`).

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

## Testing

The project ships with two layers of tests:

- **Unit tests** — `TransactionServiceTest` mocks the repository with Mockito to validate the service behaviour in isolation, covering save success, the duplicate-id failure path, delegation of `getTransactionsByType`, and the empty-match case. `InMemoryTransactionRepositoryTest` exercises the repository directly with plain JUnit: insertion semantics, duplicate-id handling, returning every id for a given type, isolation between types, case sensitivity of the type key, and the guarantee that a rejected duplicate does not leak into the type index.
- **Integration tests** — `TransactionControllerIntegrationTest` uses `@SpringBootTest` + `MockMvc` to exercise both endpoints end-to-end. `PUT /transactions/{transaction_id}` is covered for the happy path, duplicate id, missing required fields, optional `parent_id`, snake_case mapping, and malformed JSON. `GET /transactions/types/{type}` is covered for the multi-id happy path, the empty-result case, type isolation across two coexisting types, and the literal example from the spec (`PUT /transactions/10 { "amount": 5000, "type": "cars" }` → `GET /transactions/types/cars` ⇒ `[10]`). Because the in-memory repository bean is shared across the Spring context, tests draw ids and types from atomic counters to stay isolated from each other.

Run the full suite with:

```bash
./mvnw test
```


