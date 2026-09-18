package com.diplom.chatservice.dto.diary;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DiaryDayResponse(
        UUID roomId,
        LocalDate date,
        String status,
        boolean writable,
        String summary,
        long turnCount,
        String title,
        OffsetDateTime createdAt,
        OffsetDateTime closedAt
) {}
