package com.diplom.userservice.dto;

public record ProfileUpdateRequest(
    String fullName,
    String username,
    String bio,
    String avatarUrl,
    String psychProfile
) {
}
