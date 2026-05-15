package org.example.mendel_challenge.transaction.exceptions;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(long transactionId) {
        super("Transaction with id " + transactionId + " does not exist.");
    }
}
