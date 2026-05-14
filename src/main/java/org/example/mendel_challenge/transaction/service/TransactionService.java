package org.example.mendel_challenge.transaction.service;

import lombok.RequiredArgsConstructor;
import org.example.mendel_challenge.transaction.repository.InMemoryTransactionRepository;
import org.example.mendel_challenge.transaction.domain.Transaction;
import org.example.mendel_challenge.transaction.exceptions.TransactionAlreadyExistsException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final InMemoryTransactionRepository repository;

    public Transaction save(Transaction transaction) {
        // Single Atomic call to check if it already exists
        Optional<Transaction> existing = repository.saveIfAbsent(transaction);
        if( existing.isPresent()) {
            throw new TransactionAlreadyExistsException(transaction.getId());
        }
        return transaction;
    }
}
