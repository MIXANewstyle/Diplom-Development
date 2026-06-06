package com.diplom.chatservice.consumer.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RoleUpdatedEvent(
        UUID userId,
        Integer roleId,
        OffsetDateTime occurredAt
) {}
