package com.diplom.contentservice.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CommentModeratedEvent(
    UUID commentId,
    UUID postId,
    UUID actorId,
    String action,
    OffsetDateTime occurredAt
) {}
