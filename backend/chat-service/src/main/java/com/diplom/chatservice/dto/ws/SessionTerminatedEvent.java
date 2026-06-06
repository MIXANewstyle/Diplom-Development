package com.diplom.chatservice.dto.ws;

public record SessionTerminatedEvent(
        String type,
        String reason
) {
    public SessionTerminatedEvent(String reason) {
        this("SESSION_TERMINATED", reason);
    }
}
