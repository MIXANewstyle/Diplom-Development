package com.diplom.chatservice.dto.ws;

import java.util.UUID;

public record DialogueStartedEvent(
    String type,
    UUID currentFloorParticipantId
) {
    public static DialogueStartedEvent of(UUID currentFloorParticipantId) {
        return new DialogueStartedEvent("DIALOGUE_STARTED", currentFloorParticipantId);
    }
}
