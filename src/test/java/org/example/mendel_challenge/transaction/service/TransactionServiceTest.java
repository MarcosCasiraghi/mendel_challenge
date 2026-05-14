package org.example.mendel_challenge.transaction.service;

import org.example.mendel_challenge.transaction.domain.Transaction;
import org.example.mendel_challenge.transaction.exceptions.TransactionAlreadyExistsException;
import org.example.mendel_challenge.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
}
