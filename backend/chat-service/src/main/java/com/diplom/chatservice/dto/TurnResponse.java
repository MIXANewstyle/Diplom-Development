package com.diplom.chatservice.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TurnResponse(
    UUID id,
    UUID roomId,
    Integer seq,
    String role,
    UUID participantId,
    String content,
    Integer promptTokens,
    Integer completionTokens,
    OffsetDateTime createdAt
) {}
