package com.diplom.billingservice.exception;

public class TrialAlreadyUsedException extends RuntimeException {
    public TrialAlreadyUsedException(String message) {
        super(message);
    }
}
