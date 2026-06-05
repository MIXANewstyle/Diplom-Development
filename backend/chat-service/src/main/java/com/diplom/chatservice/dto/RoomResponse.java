package com.diplom.chatservice.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RoomResponse(
    UUID id,
    String type,
    String status,
    String phase,
    UUID currentFloorParticipantId,
    String aiModel,
    UUID ownerUserId,
    OffsetDateTime createdAt,
    OffsetDateTime startedAt,
    List<ParticipantResponse> participants
) {}
