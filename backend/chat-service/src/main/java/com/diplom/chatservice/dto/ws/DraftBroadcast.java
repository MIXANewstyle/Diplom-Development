package com.diplom.chatservice.dto.ws;

import java.util.UUID;

/**
 * Outbound payload broadcast to {@code /topic/rooms/{roomId}} when a draft bubble
 * is inserted, updated, or deleted.
 *
 * <p>For {@code op = "DELETE"}, {@code text} is null.
 */
public record DraftBroadcast(
    String type,
    UUID participantId,
    UUID bubbleId,
    String text,
    String op
) {
    public static DraftBroadcast upsert(UUID participantId, UUID bubbleId, String text) {
        return new DraftBroadcast("DRAFT_BROADCAST", participantId, bubbleId, text, "UPSERT");
    }

    public static DraftBroadcast delete(UUID participantId, UUID bubbleId) {
        return new DraftBroadcast("DRAFT_BROADCAST", participantId, bubbleId, null, "DELETE");
    }
}
