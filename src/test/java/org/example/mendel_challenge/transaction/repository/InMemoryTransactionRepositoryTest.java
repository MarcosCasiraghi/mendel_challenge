package org.example.mendel_challenge.transaction.repository;

import org.example.mendel_challenge.transaction.domain.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

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
}
