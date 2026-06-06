package com.diplom.chatservice.consumer.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountModeratedEvent(
        UUID userId,
        Integer statusId,
        OffsetDateTime occurredAt
) {}
