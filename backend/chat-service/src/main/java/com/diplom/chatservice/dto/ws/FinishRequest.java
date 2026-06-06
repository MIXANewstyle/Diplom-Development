package com.diplom.chatservice.dto.ws;

/**
 * Inbound payload for {@code /app/rooms/{roomId}/finish}.
 * JSON integer binds to {@link Long}.
 */
public record FinishRequest(
        Long turnSeq
) {
}
