package com.diplom.userservice.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuthorUnfollowedEvent(
        UUID followerId,
        UUID authorId,
        OffsetDateTime occurredAt
) {}
