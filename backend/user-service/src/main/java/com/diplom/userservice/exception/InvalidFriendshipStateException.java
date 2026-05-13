package com.diplom.userservice.exception;

public class InvalidFriendshipStateException extends RuntimeException {
    public InvalidFriendshipStateException(String message) {
        super(message);
    }
}
