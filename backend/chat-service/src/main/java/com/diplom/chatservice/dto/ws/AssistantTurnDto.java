package com.diplom.chatservice.dto.ws;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AssistantTurnDto(
        UUID id,
        int seq,
        String content,
        OffsetDateTime createdAt
) {
}
