package com.diplom.chatservice.dto.ws;

/**
 * Inbound payload for {@code /app/rooms/{roomId}/presence}.
 *
 * <p>{@code away == true} marks the caller offline (tab hidden);
 * {@code away == false} restores online (tab visible again).
 */
public record PresenceCommand(
        boolean away
) {
}
