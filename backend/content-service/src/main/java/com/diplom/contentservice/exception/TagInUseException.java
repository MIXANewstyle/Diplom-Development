package com.diplom.contentservice.exception;

public class TagInUseException extends RuntimeException {
    public TagInUseException(String message) {
        super(message);
    }
}
