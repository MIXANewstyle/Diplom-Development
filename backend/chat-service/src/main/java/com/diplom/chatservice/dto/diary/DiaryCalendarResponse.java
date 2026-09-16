package com.diplom.chatservice.dto.diary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DiaryCalendarResponse(
        String month,
        LocalDate serverToday,
        List<Day> days,
        List<DiaryPeriodResponse> weeks,
        DiaryPeriodResponse monthPeriod
) {
    public record Day(
            LocalDate date,
            UUID roomId,
            String status,
            long turnCount,
            boolean hasSummary,
            boolean writable
    ) {}
}
