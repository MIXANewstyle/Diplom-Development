package com.diplom.chatservice.dto.ws;

import java.util.UUID;

/**
 * A single bubble in the draft buffer. Stored as part of a JSON array in Redis.
 */
public record DraftBubble(
    UUID bubbleId,
    String text
) {}
