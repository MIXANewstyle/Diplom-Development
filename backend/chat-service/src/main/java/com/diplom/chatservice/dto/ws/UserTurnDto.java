package com.diplom.chatservice.dto.ws;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserTurnDto(
        UUID id,
        int seq,
        UUID participantId,
        String content,
        OffsetDateTime createdAt
) {
}
