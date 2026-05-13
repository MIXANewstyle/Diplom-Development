package com.diplom.userservice.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FollowedAuthorResponse(
        UUID authorId,
        OffsetDateTime followedAt
) {}
