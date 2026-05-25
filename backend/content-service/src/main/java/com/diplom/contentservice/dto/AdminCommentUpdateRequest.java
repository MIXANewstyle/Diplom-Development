package com.diplom.contentservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCommentUpdateRequest(
    @NotBlank @Size(min = 1, max = 2000) String content
) {}
