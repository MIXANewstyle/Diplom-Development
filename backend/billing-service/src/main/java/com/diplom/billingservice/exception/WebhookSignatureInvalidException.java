package com.diplom.billingservice.exception;

public class WebhookSignatureInvalidException extends RuntimeException {
    public WebhookSignatureInvalidException(String message) {
        super(message);
    }
}
