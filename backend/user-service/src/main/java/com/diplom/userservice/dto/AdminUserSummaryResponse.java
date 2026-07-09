package com.diplom.userservice.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUserSummaryResponse(
        UUID id,
        String email,
        String username,
        String fullName,
        String avatarUrl,
        Integer roleId,
        Integer statusId,
        OffsetDateTime createdAt
) {}
