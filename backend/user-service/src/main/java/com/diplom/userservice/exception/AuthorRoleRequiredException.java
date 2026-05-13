package com.diplom.userservice.exception;

public class AuthorRoleRequiredException extends RuntimeException {
    public AuthorRoleRequiredException(String message) {
        super(message);
    }
}
