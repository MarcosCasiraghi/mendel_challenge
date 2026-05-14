package org.example.mendel_challenge.transaction.exceptions;

public class TransactionAlreadyExistsException extends RuntimeException {

    public TransactionAlreadyExistsException(long transactionId) {
        super("Transaction with id " + transactionId + " already exists.");
    }
}
