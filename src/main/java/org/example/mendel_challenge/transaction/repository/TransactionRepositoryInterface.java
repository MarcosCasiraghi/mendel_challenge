package org.example.mendel_challenge.transaction.repository;

import org.example.mendel_challenge.transaction.domain.Transaction;

import java.util.Optional;

public interface TransactionRepositoryInterface {

    Optional<Transaction> saveIfAbsent(Transaction transaction);
}
