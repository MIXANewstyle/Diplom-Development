package com.diplom.chatservice.exception;

public class SubscriptionRequiredException extends RuntimeException {
    public SubscriptionRequiredException() {
        super("An active subscription is required");
    }

    public SubscriptionRequiredException(String message) {
        super(message);
    }
}
