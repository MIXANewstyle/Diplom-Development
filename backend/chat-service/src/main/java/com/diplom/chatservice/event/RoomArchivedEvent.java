package com.diplom.chatservice.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RoomArchivedEvent(
    OffsetDateTime occurredAt,
    UUID roomId,
    String type,
    List<UUID> participantUserIds,
    OffsetDateTime endedAt
) {}
