package org.example.mendel_challenge.transaction.repository;

import org.example.mendel_challenge.transaction.domain.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class InMemoryTransactionRepositoryTest {

    private InMemoryTransactionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTransactionRepository();
    }

    @Test
    @DisplayName("saveIfAbsent() returns empty when the id is new")
    void saveIfAbsent_returnsEmpty_whenIdIsNew() {
        Transaction tx = Transaction.builder()
                .id(1L)
                .amount(10.0)
                .type("cars")
                .build();

        Optional<Transaction> result = repository.saveIfAbsent(tx);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("saveIfAbsent() returns the existing transaction when the id is already stored")
    void saveIfAbsent_returnsExisting_whenIdAlreadyExists() {
        Transaction first  = Transaction.builder().id(1L).amount(10.0).type("cars").build();
        Transaction second = Transaction.builder().id(1L).amount(99.0).type("shopping").build();
        repository.saveIfAbsent(first);

        Optional<Transaction> result = repository.saveIfAbsent(second);

        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(first);
    }

    @Test
    @DisplayName("getTransactionsByType() returns an empty set when no transaction of that type exists")
    void getTransactionsByType_returnsEmpty_whenTypeUnknown() {
        Set<Long> result = repository.getTransactionsByType("missing");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getTransactionsByType() returns every id stored under that type")
    void getTransactionsByType_returnsAllIdsForType() {
        repository.saveIfAbsent(Transaction.builder().id(1L).amount(10.0).type("cars").build());
        repository.saveIfAbsent(Transaction.builder().id(2L).amount(20.0).type("cars").build());
        repository.saveIfAbsent(Transaction.builder().id(3L).amount(30.0).type("cars").build());

        Set<Long> result = repository.getTransactionsByType("cars");

        assertThat(result).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    @DisplayName("getTransactionsByType() returns only ids of the requested type")
    void getTransactionsByType_returnsOnlyMatchingType() {
        repository.saveIfAbsent(Transaction.builder().id(1L).amount(10.0).type("cars").build());
        repository.saveIfAbsent(Transaction.builder().id(2L).amount(20.0).type("shopping").build());
        repository.saveIfAbsent(Transaction.builder().id(3L).amount(30.0).type("cars").build());

        Set<Long> cars = repository.getTransactionsByType("cars");
        Set<Long> shopping = repository.getTransactionsByType("shopping");

        assertThat(cars).containsExactlyInAnyOrder(1L, 3L);
        assertThat(shopping).containsExactly(2L);
    }

    @Test
    @DisplayName("getTransactionsByType() is case sensitive on the type key")
    void getTransactionsByType_isCaseSensitive() {
        repository.saveIfAbsent(Transaction.builder().id(1L).amount(10.0).type("cars").build());

        assertThat(repository.getTransactionsByType("cars")).containsExactly(1L);
        assertThat(repository.getTransactionsByType("Cars")).isEmpty();
        assertThat(repository.getTransactionsByType("CARS")).isEmpty();
    }

    @Test
    @DisplayName("A rejected duplicate saveIfAbsent() does not pollute the type index")
    void saveIfAbsent_doesNotPolluteTypeIndex_whenIdAlreadyExists() {
        Transaction first  = Transaction.builder().id(1L).amount(10.0).type("cars").build();
        Transaction second = Transaction.builder().id(1L).amount(99.0).type("shopping").build();
        repository.saveIfAbsent(first);

        // duplicate save with a different type - must be ignored
        repository.saveIfAbsent(second);

        assertThat(repository.getTransactionsByType("cars")).containsExactly(1L);
        assertThat(repository.getTransactionsByType("shopping")).isEmpty();
    }

    @Test
    @DisplayName("getTransactionById() returns the stored transaction when present")
    void getTransactionById_returnsTransaction_whenPresent() {
        Transaction tx = Transaction.builder().id(1L).amount(42.0).type("cars").build();
        repository.saveIfAbsent(tx);

        Optional<Transaction> result = repository.getTransactionById(1L);

        assertThat(result).isPresent();
        assertThat(result.get()).isSameAs(tx);
    }

    @Test
    @DisplayName("getTransactionById() returns empty when the id is unknown")
    void getTransactionById_returnsEmpty_whenIdUnknown() {
        Optional<Transaction> result = repository.getTransactionById(9999L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getTransactionsSum() matches the spec example (10 -> 11 -> 12)")
    void getTransactionsSum_matchesSpecExample() {
        // PUT /transactions/10 { "amount": 5000,  "type": "cars" }
        // PUT /transactions/11 { "amount": 10000, "type": "shopping", "parent_id": 10 }
        // PUT /transactions/12 { "amount": 5000,  "type": "shopping", "parent_id": 11 }
        repository.saveIfAbsent(Transaction.builder().id(10L).amount(5000).type("cars").build());
        repository.saveIfAbsent(Transaction.builder().id(11L).amount(10000).type("shopping").parentId(10L).build());
        repository.saveIfAbsent(Transaction.builder().id(12L).amount(5000).type("shopping").parentId(11L).build());

        assertThat(repository.getTransactionsSum(10L)).isEqualTo(20000.0);
        assertThat(repository.getTransactionsSum(11L)).isEqualTo(15000.0);
        assertThat(repository.getTransactionsSum(12L)).isEqualTo(5000.0);
    }

    @Test
    @DisplayName("getTransactionsSum() walks every branch of a multi-child subtree")
    void getTransactionsSum_walksMultiBranchSubtree() {
        // Tree:
        //         1 (100)
        //        /  \
        //       2    3      (50, 30)
        //      / \    \
        //     4   5    6    (10, 15, 20)
        repository.saveIfAbsent(Transaction.builder().id(1L).amount(100).type("root").build());
        repository.saveIfAbsent(Transaction.builder().id(2L).amount(50).type("mid").parentId(1L).build());
        repository.saveIfAbsent(Transaction.builder().id(3L).amount(30).type("mid").parentId(1L).build());
        repository.saveIfAbsent(Transaction.builder().id(4L).amount(10).type("leaf").parentId(2L).build());
        repository.saveIfAbsent(Transaction.builder().id(5L).amount(15).type("leaf").parentId(2L).build());
        repository.saveIfAbsent(Transaction.builder().id(6L).amount(20).type("leaf").parentId(3L).build());

        assertThat(repository.getTransactionsSum(1L)).isEqualTo(225.0); // 100+50+30+10+15+20
        assertThat(repository.getTransactionsSum(2L)).isEqualTo(75.0);  // 50+10+15
        assertThat(repository.getTransactionsSum(3L)).isEqualTo(50.0);  // 30+20
        assertThat(repository.getTransactionsSum(4L)).isEqualTo(10.0);  // leaf
    }

    @Test
    @DisplayName("getTransactionsSum() returns the root amount when the node has no children")
    void getTransactionsSum_returnsRootAmount_whenLeaf() {
        repository.saveIfAbsent(Transaction.builder().id(1L).amount(123.45).type("cars").build());

        assertThat(repository.getTransactionsSum(1L)).isEqualTo(123.45);
    }

    @Test
    @DisplayName("getTransactionsSum() returns 0 when the root id is not in the store")
    void getTransactionsSum_returnsZero_whenRootMissing() {
        // The repository itself returns 0 for an unknown root; the service is the
        // layer that converts this into TransactionDoesNotExistException.
        double sum = repository.getTransactionsSum(9999L);

        assertThat(sum).isEqualTo(0.0);
    }

    @Test
    @DisplayName("getTransactionsSum() terminates when parent_ids form a cycle")
    void getTransactionsSum_terminates_whenCycleExists() {
        // 1 declares parent=2, 2 declares parent=1, so childrenByParentId contains
        //   1 -> {2}
        //   2 -> {1}
        // The visited-set guard inside the BFS must prevent an infinite walk.
        repository.saveIfAbsent(Transaction.builder().id(1L).amount(100).type("a").parentId(2L).build());
        repository.saveIfAbsent(Transaction.builder().id(2L).amount(50).type("b").parentId(1L).build());

        double sum = assertTimeoutPreemptively(
                Duration.ofSeconds(1),
                () -> repository.getTransactionsSum(1L)
        );

        assertThat(sum).isEqualTo(150.0);
    }
}
