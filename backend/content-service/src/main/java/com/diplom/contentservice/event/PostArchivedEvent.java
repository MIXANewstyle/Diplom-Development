package com.diplom.contentservice.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PostArchivedEvent(
    UUID postId,
    UUID authorId,
    OffsetDateTime occurredAt
) {}
