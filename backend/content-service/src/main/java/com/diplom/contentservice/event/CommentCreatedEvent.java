package com.diplom.contentservice.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CommentCreatedEvent(
    UUID commentId,
    UUID postId,
    UUID authorId,
    UUID parentId,
    OffsetDateTime occurredAt
) {}
