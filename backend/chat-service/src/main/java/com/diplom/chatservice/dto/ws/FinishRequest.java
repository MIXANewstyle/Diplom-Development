package com.diplom.chatservice.dto.ws;

/**
 * Inbound payload for {@code /app/rooms/{roomId}/finish}.
 * JSON integer binds to {@link Long}.
 *
 * <p>{@code text} is an optional fallback: if the draft buffer is empty
 * (e.g. deleted by a race), the server can still package this text into
 * a user turn instead of silently no-op-ing.</p>
 */
public record FinishRequest(
        Long turnSeq,
        String text
) {
}
