package com.diplom.contentservice.exception;

public class SelfUpvoteException extends RuntimeException {
    public SelfUpvoteException(String message) {
        super(message);
    }
}
