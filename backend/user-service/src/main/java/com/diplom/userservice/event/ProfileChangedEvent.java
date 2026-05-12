package com.diplom.userservice.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProfileChangedEvent(
        UUID userId,
        String username,
        String fullName,
        String avatarUrl,
        OffsetDateTime occurredAt
) {}
