package com.diplom.chatservice.dto.diary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DiaryMemoriesResponse(
        LocalDate date,
        List<Memory> items
) {
    /** kind: YEAR_AGO | MONTH_AGO */
    public record Memory(
            String kind,
            LocalDate date,
            UUID roomId,
            String summary,
            long turnCount
    ) {}
}
