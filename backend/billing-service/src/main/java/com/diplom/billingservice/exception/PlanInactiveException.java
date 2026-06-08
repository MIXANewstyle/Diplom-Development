package com.diplom.billingservice.exception;

public class PlanInactiveException extends RuntimeException {
    public PlanInactiveException(String message) {
        super(message);
    }
}
