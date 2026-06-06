package com.diplom.chatservice.dto.ws;

import java.util.UUID;

public record TurnChangedEvent(
        String type,
        UUID currentFloorParticipantId
) {
    public static TurnChangedEvent of(UUID currentFloorParticipantId) {
        return new TurnChangedEvent("TURN_CHANGED", currentFloorParticipantId);
    }
}
