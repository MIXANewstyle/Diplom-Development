package com.diplom.chatservice.dto.ws;

import java.util.UUID;

/**
 * Inbound payload for {@code /app/rooms/{roomId}/draft/delete}.
 * The client sends a bubbleId to remove from the draft buffer.
 */
public record DraftDeleteRequest(
    UUID bubbleId
) {}
