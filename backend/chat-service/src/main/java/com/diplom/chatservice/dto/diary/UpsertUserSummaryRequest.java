package com.diplom.chatservice.dto.diary;

import jakarta.validation.constraints.Size;

public record UpsertUserSummaryRequest(
        @Size(max = 4000) String text
) {}
