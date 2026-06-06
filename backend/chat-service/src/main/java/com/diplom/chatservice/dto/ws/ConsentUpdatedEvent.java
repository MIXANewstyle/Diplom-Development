package com.diplom.chatservice.dto.ws;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ConsentUpdatedEvent(
    String type,
    UUID participantId,
    OffsetDateTime consentStartAt
) {
    public static ConsentUpdatedEvent of(UUID participantId, OffsetDateTime consentStartAt) {
        return new ConsentUpdatedEvent("CONSENT_UPDATED", participantId, consentStartAt);
    }
}
