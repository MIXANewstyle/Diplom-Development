package com.diplom.chatservice.dto.ws;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DialogueArchivedEvent(
    String type,
    UUID roomId,
    OffsetDateTime endedAt
) {
    public static DialogueArchivedEvent of(UUID roomId, OffsetDateTime endedAt) {
        return new DialogueArchivedEvent("DIALOGUE_ARCHIVED", roomId, endedAt);
    }
}
