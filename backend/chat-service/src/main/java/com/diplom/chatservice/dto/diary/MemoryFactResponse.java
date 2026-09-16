package com.diplom.chatservice.dto.diary;

import java.time.LocalDate;
import java.util.UUID;

public record MemoryFactResponse(
        UUID id,
        String category,
        String content,
        LocalDate firstSeenDate,
        LocalDate lastConfirmedDate
) {}
