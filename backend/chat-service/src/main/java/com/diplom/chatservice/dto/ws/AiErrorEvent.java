package com.diplom.chatservice.dto.ws;

public record AiErrorEvent(
        String type,
        String message
) {
    public static AiErrorEvent of(String message) {
        return new AiErrorEvent("AI_ERROR", message);
    }
}
