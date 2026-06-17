package com.diplom.contentservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PostCreateRequest(
    @NotBlank @Size(min = 1, max = 255) String title,
    @Size(max = 102400) String content,
    @Size(max = 10) List<@Pattern(regexp = "^(https://.+|/api/v1/uploads/files/[a-zA-Z0-9._-]+)$") String> imageUrls,
    @Size(max = 5) Set<UUID> tagIds,
    @Size(max = 20) List<@Size(min = 1, max = 50) String> keywords
) {}
