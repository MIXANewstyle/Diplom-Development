package com.diplom.chatservice.exception;

public class NotYourTurnException extends RuntimeException {
    public NotYourTurnException(String message) {
        super(message);
    }
}
