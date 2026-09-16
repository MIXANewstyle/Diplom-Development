package com.diplom.chatservice.dto.diary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMemoryFactRequest(
        @NotBlank @Size(max = 300) String content
) {}
