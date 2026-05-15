package org.example.mendel_challenge.transaction.repository;

import org.example.mendel_challenge.transaction.domain.Transaction;

import java.util.Optional;
import java.util.Set;

public interface TransactionRepository {

    Optional<Transaction> saveIfAbsent(Transaction transaction);

    Set<Long> getTransactionsByType(String type);
}
