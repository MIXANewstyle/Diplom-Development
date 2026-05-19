package com.diplom.contentservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CommentCreateRequest(
    @NotBlank @Size(min = 1, max = 2000) String content,
    UUID parentId
) {}
