package com.diplom.chatservice.dto.ws;

import java.util.UUID;

public record EndDeclinedEvent(
    String type,
    UUID currentFloorParticipantId
) {
    public static EndDeclinedEvent of(UUID currentFloorParticipantId) {
        return new EndDeclinedEvent("END_DECLINED", currentFloorParticipantId);
    }
}
