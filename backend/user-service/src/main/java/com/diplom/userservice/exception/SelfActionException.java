package com.diplom.userservice.exception;

public class SelfActionException extends RuntimeException {
    public SelfActionException(String message) {
        super(message);
    }
}
