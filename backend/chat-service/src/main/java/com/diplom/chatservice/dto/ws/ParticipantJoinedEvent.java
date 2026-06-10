package com.diplom.chatservice.dto.ws;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ParticipantJoinedEvent(
    String type,
    UUID participantId,
    OffsetDateTime joinedAt,
    String roomStatus
) {
    public static ParticipantJoinedEvent of(UUID participantId, OffsetDateTime joinedAt, String roomStatus) {
        return new ParticipantJoinedEvent("PARTICIPANT_JOINED", participantId, joinedAt, roomStatus);
    }
}
