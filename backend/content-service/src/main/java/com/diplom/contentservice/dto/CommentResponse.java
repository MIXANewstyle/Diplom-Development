package com.diplom.contentservice.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CommentResponse(
    UUID id,
    UUID postId,
    UUID authorId,
    String authorUsername,
    String authorAvatarUrl,
    UUID parentId,
    String content,
    boolean deleted,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    Long repliesCount
) {}
