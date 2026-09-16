package com.diplom.chatservice.service;

import com.diplom.chatservice.config.ChatLlmProperties;
import com.diplom.chatservice.entity.DiaryPeriod;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.exception.DiaryPeriodEmptyException;
import com.diplom.chatservice.exception.LlmUnavailableException;
import com.diplom.chatservice.llm.LlmClient;
import com.diplom.chatservice.llm.LlmMessage;
import com.diplom.chatservice.llm.LlmRequest;
import com.diplom.chatservice.llm.LlmResponse;
import com.diplom.chatservice.repository.DiaryPeriodRepository;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.repository.TurnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Week (ISO) and month periods: the author's own summary and the automatic one.
 *
 * <p>Automatic summaries are built from <em>day</em> summaries for both period types (ISO weeks
 * straddle month boundaries, so a month is not a sum of weeks). The author's period texts are fed
 * in as "своими словами" so the automatic summary stays consistent with how the author sees it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryPeriodService {

    private static final int ROOM_STATUS_ARCHIVED = 5;

    private final DiaryPeriodRepository periodRepository;
    private final RoomRepository roomRepository;
    private final TurnRepository turnRepository;
    private final LlmClient llmClient;
    private final ChatLlmProperties llmProperties;
    private final DiaryDateService dates;
    private final DiaryMemoryIndexer indexer;
    private final RateLimitService rateLimitService;

    // ==================== lookup ====================

    public LocalDate normalizeStart(int periodTypeId, LocalDate anyDateInPeriod) {
        return periodTypeId == DiaryPeriod.TYPE_MONTH ? dates.monthStart(anyDateInPeriod) : dates.weekStart(anyDateInPeriod);
    }

    public LocalDate periodEnd(int periodTypeId, LocalDate start) {
        return periodTypeId == DiaryPeriod.TYPE_MONTH ? dates.monthEnd(start) : start.plusDays(6);
    }

    @Transactional(readOnly = true)
    public Optional<DiaryPeriod> find(UUID owner, int periodTypeId, LocalDate anyDateInPeriod) {
        return periodRepository.findByOwnerUserIdAndPeriodTypeIdAndPeriodStart(owner, periodTypeId, normalizeStart(periodTypeId, anyDateInPeriod));
    }

    /** Periods of a type that overlap [from, to], ordered by start. */
    @Transactional(readOnly = true)
    public List<DiaryPeriod> findOverlapping(UUID owner, int periodTypeId, LocalDate from, LocalDate to) {
        return periodRepository.findByOwnerUserIdAndPeriodTypeIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualOrderByPeriodStartAsc(
                owner, periodTypeId, to, from);
    }

    public DiaryPeriod getOrCreate(UUID owner, int periodTypeId, LocalDate anyDateInPeriod) {
        LocalDate start = normalizeStart(periodTypeId, anyDateInPeriod);
        Optional<DiaryPeriod> existing = periodRepository.findByOwnerUserIdAndPeriodTypeIdAndPeriodStart(owner, periodTypeId, start);
        if (existing.isPresent()) return existing.get();
        try {
            return periodRepository.save(DiaryPeriod.builder()
                    .ownerUserId(owner)
                    .periodTypeId(periodTypeId)
                    .periodStart(start)
                    .periodEnd(periodEnd(periodTypeId, start))
                    .build());
        } catch (DataIntegrityViolationException race) {
            return periodRepository.findByOwnerUserIdAndPeriodTypeIdAndPeriodStart(owner, periodTypeId, start)
                    .orElseThrow(() -> race);
        }
    }

    // ==================== author's summary ====================

    @Transactional
    public DiaryPeriod saveUserSummary(UUID owner, int periodTypeId, LocalDate anyDateInPeriod, String text) {
        DiaryPeriod period = getOrCreate(owner, periodTypeId, anyDateInPeriod);
        String normalized = text == null ? null : text.strip();
        period.setUserSummary(normalized == null || normalized.isEmpty() ? null : normalized);
        period.setUserUpdatedAt(OffsetDateTime.now());
        return periodRepository.save(period);
    }

    // ==================== automatic summary ====================

    /** User-triggered (re)generation; rate limited per user. */
    public DiaryPeriod generateOnDemand(UUID owner, int periodTypeId, LocalDate anyDateInPeriod) {
        if (rateLimitService.checkDiarySummaryRate(owner)) {
            throw new com.diplom.chatservice.exception.RateLimitExceededException("Too many summary generations, try later");
        }
        LocalDate start = normalizeStart(periodTypeId, anyDateInPeriod);
        if (start.isAfter(dates.today())) {
            throw new DiaryPeriodEmptyException("Period has not started yet");
        }
        DiaryPeriod result = generateAutoSummary(owner, periodTypeId, start);
        if (result == null) {
            throw new DiaryPeriodEmptyException("No summarized days in this period yet");
        }
        return result;
    }

    /**
     * Builds the automatic summary from the day summaries inside the period (up to today).
     * Returns {@code null} when there is nothing to summarize yet.
     */
    public DiaryPeriod generateAutoSummary(UUID owner, int periodTypeId, LocalDate start) {
        LocalDate end = periodEnd(periodTypeId, start);
        LocalDate upTo = end.isBefore(dates.today()) ? end : dates.today();
        List<Room> days = roomRepository.findByOwnerUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(owner, start, upTo);
        List<Room> summarized = days.stream()
                .filter(r -> r.getRunningSummary() != null && !r.getRunningSummary().isBlank())
                .toList();
        if (summarized.isEmpty()) {
            return null;
        }

        DiaryPeriod period = getOrCreate(owner, periodTypeId, start);
        String input = buildInput(owner, periodTypeId, period, summarized);
        String prompt = periodTypeId == DiaryPeriod.TYPE_MONTH
                ? llmProperties.prompts().diaryMonthSummary()
                : llmProperties.prompts().diaryWeekSummary();

        LlmRequest request = new LlmRequest(prompt, List.of(new LlmMessage("user", input)),
                llmProperties.diary().maxOutputTokens(), 0.3, llmProperties.models().summaryOrNull());
        LlmResponse response;
        try {
            response = llmClient.complete(request);
        } catch (LlmUnavailableException e) {
            log.warn("Period summary LLM call failed for owner {} {} {}: {}", owner, periodTypeId, start, e.getMessage());
            throw e;
        }
        rateLimitService.addDiaryDailyTokens(owner, response.totalTokens());

        boolean complete = dates.isPeriodDue(end);
        LocalDate through = complete ? end : summarized.get(summarized.size() - 1).getDiaryDate();
        DiaryPeriod saved = saveAuto(period.getId(), response.content(), through);
        indexer.indexPeriodSummary(saved);
        log.info("Period summary generated: owner={} type={} start={} through={} promptTokens={} completionTokens={}",
                owner, periodTypeId, start, through, response.promptTokens(), response.completionTokens());
        return saved;
    }

    @Transactional
    protected DiaryPeriod saveAuto(UUID periodId, String summary, LocalDate through) {
        DiaryPeriod period = periodRepository.findById(periodId).orElseThrow();
        period.setAutoSummary(summary != null ? summary.strip() : null);
        period.setAutoSummaryThrough(through);
        period.setAutoGeneratedAt(OffsetDateTime.now());
        return periodRepository.save(period);
    }

    private String buildInput(UUID owner, int periodTypeId, DiaryPeriod period, List<Room> summarized) {
        StringBuilder sb = new StringBuilder();
        sb.append(periodTypeId == DiaryPeriod.TYPE_MONTH ? "Месяц: " : "Неделя: ")
                .append(dates.formatRange(period.getPeriodStart(), period.getPeriodEnd())).append("\n\n");
        sb.append("Итоги дней:\n");
        for (Room r : summarized) {
            sb.append("— ").append(dates.formatLong(r.getDiaryDate())).append(":\n")
                    .append(r.getRunningSummary().strip()).append("\n\n");
        }
        if (periodTypeId == DiaryPeriod.TYPE_MONTH) {
            List<DiaryPeriod> weeks = findOverlapping(owner, DiaryPeriod.TYPE_WEEK, period.getPeriodStart(), period.getPeriodEnd());
            List<DiaryPeriod> withUser = weeks.stream().filter(w -> w.getUserSummary() != null).toList();
            if (!withUser.isEmpty()) {
                sb.append("Итоги недель своими словами автора:\n");
                for (DiaryPeriod w : withUser) {
                    sb.append("— ").append(dates.formatRange(w.getPeriodStart(), w.getPeriodEnd())).append(": ")
                            .append(w.getUserSummary().strip()).append("\n\n");
                }
            }
        }
        if (period.getUserSummary() != null && !period.getUserSummary().isBlank()) {
            sb.append("Итог периода своими словами автора:\n").append(period.getUserSummary().strip()).append("\n");
        }
        return sb.toString().strip();
    }

    // ==================== sweep ====================

    /**
     * Generates due automatic summaries (period closed, every day archived and summarized) for the
     * last few closed weeks and months. Idempotent: a period whose {@code auto_summary_through}
     * already equals its end is skipped. Returns the number of summaries generated.
     */
    public int generateDueSummaries(int maxPerRun) {
        int generated = 0;
        List<Candidate> candidates = new ArrayList<>();
        LocalDate today = dates.today();
        LocalDate lastClosedWeekStart = dates.weekStart(today).minusWeeks(1);
        for (int i = 0; i < 4; i++) {
            LocalDate start = lastClosedWeekStart.minusWeeks(i);
            candidates.add(new Candidate(DiaryPeriod.TYPE_WEEK, start, start.plusDays(6)));
        }
        LocalDate lastClosedMonthStart = dates.monthStart(today).minusMonths(1);
        for (int i = 0; i < 2; i++) {
            LocalDate start = lastClosedMonthStart.minusMonths(i);
            candidates.add(new Candidate(DiaryPeriod.TYPE_MONTH, start, dates.monthEnd(start)));
        }

        for (Candidate c : candidates) {
            LocalDate start = c.start(), end = c.end();
            int type = c.type();
            if (!dates.isPeriodDue(end)) continue;
            List<UUID> owners;
            try {
                owners = roomRepository.findDiaryOwnersWithEntriesBetween(start, end);
            } catch (Exception e) {
                log.warn("Period sweep query failed: {}", e.getMessage());
                continue;
            }
            for (UUID owner : owners) {
                if (generated >= maxPerRun) return generated;
                try {
                    Optional<DiaryPeriod> existing = periodRepository.findByOwnerUserIdAndPeriodTypeIdAndPeriodStart(owner, type, start);
                    if (existing.isPresent() && existing.get().getAutoSummaryThrough() != null
                            && !existing.get().getAutoSummaryThrough().isBefore(end)) {
                        continue; // already complete
                    }
                    if (!allDaysSummarized(owner, start, end)) {
                        log.debug("Period {}..{} for owner {} still has unsummarized days, waiting", start, end, owner);
                        continue;
                    }
                    if (generateAutoSummary(owner, type, start) != null) {
                        generated++;
                    }
                } catch (Exception e) {
                    log.warn("Period summary generation failed for owner {} {}..{}: {}", owner, start, end, e.getMessage());
                }
            }
        }
        return generated;
    }

    private record Candidate(int type, LocalDate start, LocalDate end) {}

    private boolean allDaysSummarized(UUID owner, LocalDate start, LocalDate end) {
        List<Room> days = roomRepository.findByOwnerUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(owner, start, end);
        for (Room r : days) {
            if (r.getStatusId() != ROOM_STATUS_ARCHIVED) return false;
            boolean hasSummary = r.getRunningSummary() != null && !r.getRunningSummary().isBlank();
            if (!hasSummary && turnRepository.countByRoomId(r.getId()) > 0) return false;
        }
        return true;
    }
}
