package org.example.mendel_challenge.transaction.repository;

import org.example.mendel_challenge.transaction.domain.Transaction;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryTransactionRepository implements TransactionRepository {
    private final Map<Long, Transaction> transactionById = new ConcurrentHashMap<>();

    @Override
    public Optional<Transaction> saveIfAbsent(Transaction transaction) {
        Transaction previous = transactionById.putIfAbsent(transaction.getId(), transaction);
        return Optional.ofNullable(previous);
    }
}
