package com.diplom.chatservice.dto.ws;

import java.util.UUID;

public record EndProposedEvent(
    String type,
    UUID proposerParticipantId
) {
    public static EndProposedEvent of(UUID proposerParticipantId) {
        return new EndProposedEvent("END_PROPOSED", proposerParticipantId);
    }
}
