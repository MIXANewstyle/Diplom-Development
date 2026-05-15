package com.diplom.contentservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TagCreateRequest(
    @NotBlank @Size(min = 1, max = 50) String name
) {}
