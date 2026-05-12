package com.diplom.userservice.dto;

import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
    @Size(min = 1, max = 255) String fullName,
    @Size(min = 3, max = 100) String username,
    @Size(max = 5000) String bio,
    @Size(max = 2048) String avatarUrl,
    @Size(max = 1000) String psychProfile
) {
}
