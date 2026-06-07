package com.diplom.chatservice.dto.admin;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminRoomInspectionView(
        RoomInfo room,
        List<ParticipantInfo> participants,
        List<TurnInfo> transcript
) {
    public record RoomInfo(
            UUID id,
            Integer type,
            Integer status,
            String phase,
            UUID currentFloorParticipantId,
            UUID ownerUserId,
            String aiModel,
            UUID seedContextRoomId,
            OffsetDateTime createdAt,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            Integer version
    ) {}

    public record ParticipantInfo(
            Integer role,
            UUID userId,
            String guestDisplayName,
            Integer guestGenderId,
            Integer guestAge,
            OffsetDateTime consentStartAt,
            OffsetDateTime joinedAt,
            OffsetDateTime lastSeenAt
    ) {}

    public record TurnInfo(
            Integer seq,
            Integer role,
            UUID participantId,
            String content,
            Integer promptTokens,
            Integer completionTokens,
            OffsetDateTime createdAt
    ) {}
}
