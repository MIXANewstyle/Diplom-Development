package com.diplom.contentservice.exception;

public class InvalidCommentStateException extends RuntimeException {
    public InvalidCommentStateException(String message) {
        super(message);
    }
}
