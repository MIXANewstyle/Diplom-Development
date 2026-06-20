package com.diplom.chatservice.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RoomSummaryResponse(
    UUID id,
    String title,
    String type,
    String status,
    String myRole,
    OffsetDateTime createdAt,
    OffsetDateTime startedAt,
    String otherParticipantDisplayName,
    String otherParticipantAvatarUrl
) {}
