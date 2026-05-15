package org.example.mendel_challenge.transaction.repository;

import org.example.mendel_challenge.transaction.domain.Transaction;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryTransactionRepository implements TransactionRepository {
    private final Map<Long, Transaction> transactionById = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> transactionsByType = new ConcurrentHashMap<>();

    @Override
    public Optional<Transaction> saveIfAbsent(Transaction transaction) {
        Transaction previous = transactionById.putIfAbsent(transaction.getId(), transaction);
        if (previous == null){
            transactionsByType.computeIfAbsent(transaction.getType(), t -> ConcurrentHashMap.newKeySet()).add(transaction.getId());
        }
        return Optional.ofNullable(previous);
    }

    @Override
    public Set<Long> getTransactionsByType(String type) {
        return Set.copyOf(transactionsByType.getOrDefault(type, Set.of()));
    }
}
