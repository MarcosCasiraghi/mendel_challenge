package org.example.mendel_challenge.transaction.service;

import org.example.mendel_challenge.transaction.domain.Transaction;
import org.example.mendel_challenge.transaction.exceptions.TransactionAlreadyExistsException;
import org.example.mendel_challenge.transaction.exceptions.TransactionDoesNotExistException;
import org.example.mendel_challenge.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository repository;

    @InjectMocks
    private TransactionService transactionService;

    private Transaction sampleTransaction;

    @BeforeEach
    void setUp() {
        sampleTransaction = Transaction.builder()
                .id(1L)
                .amount(100.0)
                .type("cars")
                .parentId(null)
                .build();
    }

    @Test
    @DisplayName("save() returns the transaction when the id is new")
    void save_returnsTransaction_whenIdIsNew() {
        when(repository.saveIfAbsent(sampleTransaction)).thenReturn(Optional.empty());

        Transaction result = transactionService.save(sampleTransaction);

        assertThat(result).isSameAs(sampleTransaction);
        verify(repository).saveIfAbsent(sampleTransaction);
    }

    @Test
    @DisplayName("save() throws TransactionAlreadyExistsException when the id is already taken")
    void save_throws_whenIdAlreadyExists() {
        Transaction existing = Transaction.builder()
                .id(1L)
                .amount(999.0)
                .type("shopping")
                .build();
        when(repository.saveIfAbsent(sampleTransaction)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transactionService.save(sampleTransaction))
                .isInstanceOf(TransactionAlreadyExistsException.class)
                .hasMessageContaining("1");

        verify(repository).saveIfAbsent(sampleTransaction);
    }

    @Test
    @DisplayName("getTransactionsByType() returns the ids reported by the repository")
    void getTransactionsByType_returnsIdsFromRepository() {
        Set<Long> ids = Set.of(10L, 11L, 12L);
        when(repository.getTransactionsByType("cars")).thenReturn(ids);

        Set<Long> result = transactionService.getTransactionsByType("cars");

        assertThat(result).containsExactlyInAnyOrder(10L, 11L, 12L);
        verify(repository).getTransactionsByType("cars");
    }

    @Test
    @DisplayName("getTransactionsByType() returns an empty set when the repository has no match")
    void getTransactionsByType_returnsEmpty_whenRepositoryHasNoMatch() {
        when(repository.getTransactionsByType("unknown")).thenReturn(Set.of());

        Set<Long> result = transactionService.getTransactionsByType("unknown");

        assertThat(result).isEmpty();
        verify(repository).getTransactionsByType("unknown");
    }

    @Test
    @DisplayName("getTransactionsSum() returns the value computed by the repository when the root exists")
    void getTransactionsSum_returnsSumFromRepository_whenRootExists() {
        when(repository.getTransactionById(10L)).thenReturn(Optional.of(sampleTransaction));
        when(repository.getTransactionsSum(10L)).thenReturn(20000.0);

        double sum = transactionService.getTransactionsSum(10L);

        assertThat(sum).isEqualTo(20000.0);
        verify(repository).getTransactionById(10L);
        verify(repository).getTransactionsSum(10L);
    }

    @Test
    @DisplayName("getTransactionsSum() throws TransactionDoesNotExistException when the root id is unknown")
    void getTransactionsSum_throws_whenRootDoesNotExist() {
        when(repository.getTransactionById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransactionsSum(9999L))
                .isInstanceOf(TransactionDoesNotExistException.class)
                .hasMessageContaining("9999");

        verify(repository).getTransactionById(9999L);
        verify(repository, never()).getTransactionsSum(9999L);
    }
}
