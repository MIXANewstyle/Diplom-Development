package com.diplom.chatservice.dto.ws;

import java.util.UUID;

public record DialogueAbandonedEvent(
        String type,
        UUID roomId,
        String reason
) {
    public DialogueAbandonedEvent(UUID roomId, String reason) {
        this("DIALOGUE_ABANDONED", roomId, reason);
    }
}
