package org.example.mendel_challenge.transaction.repository;

import org.example.mendel_challenge.transaction.domain.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
}
