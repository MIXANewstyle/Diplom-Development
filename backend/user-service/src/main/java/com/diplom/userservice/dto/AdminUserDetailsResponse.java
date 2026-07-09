package com.diplom.userservice.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUserDetailsResponse(
        UUID id,
        String email,
        Integer roleId,
        Integer statusId,
        OffsetDateTime createdAt,
        String fullName,
        String username,
        String bio,
        String avatarUrl,
        String contactInfo,
        LocalDate birthDate,
        Integer genderId,
        OffsetDateTime profileUpdatedAt,
        // Product policy: Only expose a boolean flag, never the psychProfile content
        boolean psychProfileFilled,
        long friendsCount,
        long followersCount,
        long followingCount
) {}
