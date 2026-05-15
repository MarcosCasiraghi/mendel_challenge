package org.example.mendel_challenge.transaction.repository;

import org.example.mendel_challenge.transaction.domain.Transaction;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryTransactionRepository implements TransactionRepository {
    private final Map<Long, Transaction> transactionById = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> transactionsByType = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> childrenByParentId = new ConcurrentHashMap<>();

    @Override
    public Optional<Transaction> getTransactionById(long transactionId){
        return Optional.ofNullable(transactionById.get(transactionId));
    }

    @Override
    public Optional<Transaction> saveIfAbsent(Transaction transaction) {
        Transaction previous = transactionById.putIfAbsent(transaction.getId(), transaction);
        if (previous == null){
            transactionsByType.computeIfAbsent(transaction.getType(), t -> ConcurrentHashMap.newKeySet()).add(transaction.getId());

            if (transaction.getParentId() != null) { // Has parent
                childrenByParentId.computeIfAbsent(transaction.getParentId(), t -> ConcurrentHashMap.newKeySet()).add(transaction.getId());
            }
        }
        return Optional.ofNullable(previous);
    }

    @Override
    public Set<Long> getTransactionsByType(String type) {
        return Set.copyOf(transactionsByType.getOrDefault(type, Set.of()));
    }

    @Override
    public double getTransactionsSum(long transactionId) {
        double sum = 0;
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(transactionId);

        while(!queue.isEmpty()){
            Long currentId = queue.poll();
            if (!visited.add(currentId)) continue; // Protection against Recursive/Looped parents

            Transaction currentTransaction = transactionById.get(currentId);

            if (currentTransaction == null) continue;

            sum += currentTransaction.getAmount();
            queue.addAll(childrenByParentId.getOrDefault(currentId, Collections.emptySet()));
        }
        return sum;
    }
}
