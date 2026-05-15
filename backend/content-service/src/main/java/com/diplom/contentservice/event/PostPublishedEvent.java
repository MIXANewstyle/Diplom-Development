package com.diplom.contentservice.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PostPublishedEvent(
    UUID postId,
    UUID authorId,
    OffsetDateTime publishedAt,
    OffsetDateTime occurredAt
) {}
