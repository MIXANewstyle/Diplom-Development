package com.diplom.userservice.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MyProfileResponse(
        UUID id,
        String email,
        String role,
        String username,
        String fullName,
        String bio,
        String avatarUrl,
        String contactInfo,
        LocalDate birthDate,
        Integer genderId,
        String psychProfile,
        OffsetDateTime updatedAt
) {
}
