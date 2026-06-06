package com.diplom.chatservice.dto.ws;

import java.util.UUID;

/**
 * Inbound payload for {@code /app/rooms/{roomId}/draft/upsert}.
 * The client sends a bubble to insert or update in the draft buffer.
 */
public record DraftUpsertRequest(
    UUID bubbleId,
    String text
) {}
