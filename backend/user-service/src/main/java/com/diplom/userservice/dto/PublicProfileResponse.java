package com.diplom.userservice.dto;

import java.util.UUID;

/**
 * Public subset of a user's profile — safe to return to any authenticated caller.
 * Must NOT include: email, birthDate, genderId, psychProfile, updatedAt.
 */
public record PublicProfileResponse(
        UUID id,
        String username,
        String fullName,
        String avatarUrl,
        String bio,
        String contactInfo,
        String role
) {}
