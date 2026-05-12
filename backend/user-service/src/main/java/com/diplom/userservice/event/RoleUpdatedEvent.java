package com.diplom.userservice.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RoleUpdatedEvent(
        UUID userId,
        Integer roleId,
        OffsetDateTime occurredAt
) {}
