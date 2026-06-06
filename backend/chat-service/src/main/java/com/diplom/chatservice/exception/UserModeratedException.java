package com.diplom.chatservice.exception;

public class UserModeratedException extends RuntimeException {
    public UserModeratedException() {
        super("Account is moderated");
    }

    public UserModeratedException(String message) {
        super(message);
    }
}
