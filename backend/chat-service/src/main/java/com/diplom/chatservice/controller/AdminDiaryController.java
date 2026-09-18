package com.diplom.chatservice.controller;

import com.diplom.chatservice.dto.diary.DiaryDayResponse;
import com.diplom.chatservice.dto.diary.DiaryPeriodResponse;
import com.diplom.chatservice.entity.DiaryPeriod;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.exception.DiaryPeriodEmptyException;
import com.diplom.chatservice.exception.RoomNotFoundException;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.service.DiaryPeriodService;
import com.diplom.chatservice.service.DiaryService;
import com.diplom.chatservice.service.DiarySweepService;
import com.diplom.chatservice.service.SummarizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Operator controls for the diary pipeline. Everything the scheduled sweep does can be triggered
 * here on demand, which is how the day → week → month chain is exercised without waiting days.
 */
@RestController
@RequestMapping("/internal/v1/admin/diary")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDiaryController {

    private final DiarySweepService diarySweepService;
    private final DiaryService diaryService;
    private final DiaryPeriodService diaryPeriodService;
    private final SummarizationService summarizationService;
    private final RoomRepository roomRepository;

    /** Run one maintenance pass now (close stale days, catch up summaries/indexing, due periods). */
    @PostMapping("/sweep")
    public ResponseEntity<DiarySweepService.SweepResult> sweep() {
        return ResponseEntity.ok(diarySweepService.sweep());
    }

    /** Close a user's day immediately, whatever its date. */
    @PostMapping("/users/{userId}/days/{date}/close")
    public ResponseEntity<DiaryDayResponse> closeDay(
            @PathVariable UUID userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(diaryService.closeDay(userId, date));
    }

    /** Re-run the day summary fold synchronously (e.g. after a provider outage). */
    @PostMapping("/users/{userId}/days/{date}/resummarize")
    public ResponseEntity<DiaryDayResponse> resummarize(
            @PathVariable UUID userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Room room = roomRepository.findByOwnerUserIdAndDiaryDate(userId, date)
                .orElseThrow(() -> new RoomNotFoundException("No diary entry for " + date));
        summarizationService.foldTurnsIntoSummary(room.getId(), Integer.MAX_VALUE);
        return ResponseEntity.ok(diaryService.getDay(userId, date));
    }

    /** Generate a week/month summary now, ignoring the "period closed" schedule and the user rate limit. */
    @PostMapping("/users/{userId}/periods/{type}/{start}/generate")
    public ResponseEntity<DiaryPeriodResponse> generatePeriod(
            @PathVariable UUID userId,
            @PathVariable String type,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start) {
        int typeId = switch (type.toUpperCase()) {
            case "WEEK" -> DiaryPeriod.TYPE_WEEK;
            case "MONTH" -> DiaryPeriod.TYPE_MONTH;
            default -> throw new IllegalArgumentException("Unknown period type: " + type + " (expected WEEK or MONTH)");
        };
        LocalDate normalized = diaryPeriodService.normalizeStart(typeId, start);
        if (diaryPeriodService.generateAutoSummary(userId, typeId, normalized) == null) {
            throw new DiaryPeriodEmptyException("No summarized days in this period yet");
        }
        return ResponseEntity.ok(diaryService.getPeriod(userId, typeId, normalized));
    }
}
