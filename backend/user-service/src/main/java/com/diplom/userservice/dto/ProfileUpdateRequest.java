package com.diplom.userservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ProfileUpdateRequest(
    @Size(min = 1, max = 255) String fullName,
    @Size(min = 3, max = 100) String username,
    @Size(max = 5000) String bio,
    @Size(max = 2048) String avatarUrl,
    @Size(max = 1000) String psychProfile,
    @Size(max = 1000) String contactInfo,
    LocalDate birthDate,
    @Min(1) @Max(3) Integer genderId
) {
}
