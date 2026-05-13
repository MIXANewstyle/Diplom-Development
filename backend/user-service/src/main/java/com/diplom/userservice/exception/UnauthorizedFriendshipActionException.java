package com.diplom.userservice.exception;

public class UnauthorizedFriendshipActionException extends RuntimeException {
    public UnauthorizedFriendshipActionException(String message) {
        super(message);
    }
}
