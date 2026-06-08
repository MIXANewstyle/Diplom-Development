package com.diplom.billingservice.exception;

public class TransactionNotRefundableException extends RuntimeException {
    public TransactionNotRefundableException(String message) {
        super(message);
    }
}
