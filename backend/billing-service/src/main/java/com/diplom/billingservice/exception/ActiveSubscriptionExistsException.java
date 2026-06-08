package com.diplom.billingservice.exception;

public class ActiveSubscriptionExistsException extends RuntimeException {
    public ActiveSubscriptionExistsException(String message) {
        super(message);
    }
}
