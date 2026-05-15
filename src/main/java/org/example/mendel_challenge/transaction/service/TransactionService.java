package org.example.mendel_challenge.transaction.service;

import lombok.RequiredArgsConstructor;
import org.example.mendel_challenge.transaction.domain.Transaction;
import org.example.mendel_challenge.transaction.exceptions.TransactionAlreadyExistsException;
import org.example.mendel_challenge.transaction.exceptions.TransactionNotFoundException;
import org.example.mendel_challenge.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository repository;

    public Transaction save(Transaction transaction) {
        // Single Atomic call to check if it already exists
        Optional<Transaction> existing = repository.saveIfAbsent(transaction);
        if( existing.isPresent()) {
            throw new TransactionAlreadyExistsException(transaction.getId());
        }
        return transaction;
    }

    public Set<Long> getTransactionsByType(String type) {
        return repository.getTransactionsByType(type);
    }

    public double getTransactionsSum(long transactionId){
        Optional<Transaction> transaction = repository.getTransactionById(transactionId);
        if (transaction.isEmpty()) {
            throw new TransactionNotFoundException(transactionId);
        }
        return repository.getTransactionsSum(transactionId);
    }
}
