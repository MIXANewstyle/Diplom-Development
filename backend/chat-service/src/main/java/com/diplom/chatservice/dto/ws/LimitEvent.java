package com.diplom.chatservice.dto.ws;

public record LimitEvent(
        String type,
        String message
) {
    public static LimitEvent of(String message) {
        return new LimitEvent("LIMIT", message);
    }
}
