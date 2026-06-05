package com.diplom.chatservice.exception;

public class InvalidRoomStateException extends RuntimeException {
    public InvalidRoomStateException(String message) {
        super(message);
    }
}
