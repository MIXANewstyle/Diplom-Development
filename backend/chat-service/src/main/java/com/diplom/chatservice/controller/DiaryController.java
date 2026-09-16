package com.diplom.chatservice.controller;

import com.diplom.chatservice.dto.diary.DiaryCalendarResponse;
import com.diplom.chatservice.dto.diary.DiaryDayResponse;
import com.diplom.chatservice.dto.diary.DiaryMemoriesResponse;
import com.diplom.chatservice.dto.diary.DiaryPeriodResponse;
import com.diplom.chatservice.dto.diary.MemoryFactResponse;
import com.diplom.chatservice.dto.diary.UpdateMemoryFactRequest;
import com.diplom.chatservice.dto.diary.UpsertUserSummaryRequest;
import com.diplom.chatservice.entity.DiaryMemoryFact;
import com.diplom.chatservice.entity.DiaryPeriod;
import com.diplom.chatservice.security.CustomUserDetails;
import com.diplom.chatservice.service.DiaryMemoryFactService;
import com.diplom.chatservice.service.DiaryService;
import com.diplom.chatservice.service.MemoryFactParser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Diary API. Turns are written through the regular {@code /api/v1/rooms/{roomId}/turns} endpoints
 * using the day's {@code roomId}; this controller owns days, the calendar, periods and memory facts.
 */
@RestController
@RequestMapping("/api/v1/diary")
@RequiredArgsConstructor
@PreAuthorize("hasRole('BASIC')")
public class DiaryController {

    private final DiaryService diaryService;
    private final DiaryMemoryFactService factService;

    // ==================== calendar & days ====================

    @GetMapping("/calendar")
    public ResponseEntity<DiaryCalendarResponse> calendar(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ResponseEntity.ok(diaryService.calendar(user.getId(), month));
    }

    @PutMapping("/days/{date}")
    public ResponseEntity<DiaryDayResponse> openDay(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(diaryService.openDay(user.getId(), date));
    }

    @GetMapping("/days/{date}")
    public ResponseEntity<DiaryDayResponse> getDay(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(diaryService.getDay(user.getId(), date));
    }

    @DeleteMapping("/days/{date}")
    public ResponseEntity<Void> deleteDay(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        diaryService.deleteDay(user.getId(), date, user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/memories")
    public ResponseEntity<DiaryMemoriesResponse> memories(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(diaryService.memoriesFor(user.getId(), date));
    }

    // ==================== periods ====================

    @GetMapping("/periods")
    public ResponseEntity<DiaryPeriodResponse> getPeriod(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam String type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start) {
        return ResponseEntity.ok(diaryService.getPeriod(user.getId(), periodType(type), start));
    }

    @PutMapping("/periods/{type}/{start}/user-summary")
    public ResponseEntity<DiaryPeriodResponse> saveUserSummary(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String type,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @Valid @RequestBody UpsertUserSummaryRequest request) {
        return ResponseEntity.ok(diaryService.saveUserSummary(user.getId(), periodType(type), start, request.text()));
    }

    @PostMapping("/periods/{type}/{start}/auto-summary")
    public ResponseEntity<DiaryPeriodResponse> generateAutoSummary(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String type,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start) {
        return ResponseEntity.ok(diaryService.generateAutoSummary(user.getId(), periodType(type), start));
    }

    // ==================== memory facts ====================

    @GetMapping("/memory-facts")
    public ResponseEntity<List<MemoryFactResponse>> listFacts(@AuthenticationPrincipal CustomUserDetails user) {
        List<MemoryFactResponse> facts = factService.list(user.getId()).stream().map(this::toResponse).toList();
        return ResponseEntity.ok(facts);
    }

    @PatchMapping("/memory-facts/{id}")
    public ResponseEntity<MemoryFactResponse> updateFact(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMemoryFactRequest request) {
        return ResponseEntity.ok(toResponse(factService.update(user.getId(), id, request.content())));
    }

    @DeleteMapping("/memory-facts/{id}")
    public ResponseEntity<Void> deleteFact(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        factService.delete(user.getId(), id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private MemoryFactResponse toResponse(DiaryMemoryFact f) {
        return new MemoryFactResponse(f.getId(), MemoryFactParser.categoryName(f.getCategoryId()),
                f.getContent(), f.getFirstSeenDate(), f.getLastConfirmedDate());
    }

    private static int periodType(String type) {
        return switch (type == null ? "" : type.toUpperCase()) {
            case "WEEK" -> DiaryPeriod.TYPE_WEEK;
            case "MONTH" -> DiaryPeriod.TYPE_MONTH;
            default -> throw new IllegalArgumentException("Unknown period type: " + type + " (expected WEEK or MONTH)");
        };
    }
}
