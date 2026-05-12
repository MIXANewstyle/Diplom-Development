package com.diplom.userservice.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountModeratedEvent(
        UUID userId,
        Integer statusId,
        OffsetDateTime occurredAt
) {}
