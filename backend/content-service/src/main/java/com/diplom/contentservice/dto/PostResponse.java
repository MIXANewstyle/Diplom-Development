package com.diplom.contentservice.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PostResponse(
    UUID id,
    UUID authorId,
    String authorUsername,
    String authorAvatarUrl,
    String title,
    String content,
    String coverImageUrl,
    String status,
    OffsetDateTime publishedAt,
    OffsetDateTime updatedAt,
    Integer viewsCount,
    Integer upvotesCount,
    Integer commentsCount,
    Set<TagResponse> tags,
    List<String> keywords,
    Integer version
) {}
