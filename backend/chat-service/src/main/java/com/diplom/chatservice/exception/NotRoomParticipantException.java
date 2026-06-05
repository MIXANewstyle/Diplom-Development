package com.diplom.chatservice.exception;

public class NotRoomParticipantException extends RuntimeException {
    public NotRoomParticipantException(String message) {
        super(message);
    }
}
