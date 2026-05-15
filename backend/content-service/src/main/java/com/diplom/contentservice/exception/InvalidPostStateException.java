package com.diplom.contentservice.exception;

public class InvalidPostStateException extends RuntimeException {
    public InvalidPostStateException(String message) {
        super(message);
    }
}
