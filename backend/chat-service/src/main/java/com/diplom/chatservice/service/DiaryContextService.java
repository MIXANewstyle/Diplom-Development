package com.diplom.chatservice.service;

import com.diplom.chatservice.config.ChatLlmProperties;
import com.diplom.chatservice.entity.DiaryPeriod;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.Turn;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.repository.TurnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Assembles the diary {@code {context_block}}: the layers that are stable for the whole day and can
 * therefore be cached by the provider between turns.
 *
 * <pre>
 *  B. persona      — about (context_snapshot) + memory facts
 *  C. long memory  — previous month (auto + own words), completed weeks (auto + own words)
 *  D. recent       — day summaries of the last N days (yesterday verbatim if not summarized yet),
 *                    "this day a year / a month ago"
 * </pre>
 * Each layer is trimmed to its own character budget (tokens × 4) so one layer can never crowd out
 * the others; the fallback chain is month → weeks → days.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryContextService {

    static final int PERSONA_TOKENS = 1200;
    static final int LONG_MEMORY_TOKENS = 2000;
    static final int RECENT_TOKENS = 1800;
    private static final int ROOM_STATUS_ARCHIVED = 5;

    private final RoomRepository roomRepository;
    private final TurnRepository turnRepository;
    private final DiaryPeriodService periodService;
    private final DiaryMemoryFactService factService;
    private final DiaryDateService dates;
    private final ChatLlmProperties llmProperties;

    public String buildContextBlock(Room room, String displayName, String about) {
        LocalDate day = room.getDiaryDate();
        UUID owner = room.getOwnerUserId();
        StringBuilder sb = new StringBuilder();

        // --- header + persona (B)
        sb.append("Тип комнаты: дневник. Дата записи: ").append(dates.formatLong(day))
                .append(" (").append(day.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("ru"))).append(").\n");
        sb.append("Автор: ").append(displayName != null ? displayName : "Автор")
                .append(". О себе: ").append(about != null ? about : "не указано").append(".\n");
        String facts = factService.buildFactsBlock(owner, PERSONA_TOKENS * 4);
        if (facts != null) {
            sb.append('\n').append(facts).append('\n');
        }

        // --- long memory (C)
        String longMemory = buildLongMemory(owner, day);
        if (!longMemory.isEmpty()) {
            sb.append('\n').append(trim(longMemory, LONG_MEMORY_TOKENS * 4));
        }

        // --- recent (D)
        String recent = buildRecent(owner, day);
        if (!recent.isEmpty()) {
            sb.append('\n').append(trim(recent, RECENT_TOKENS * 4));
        }

        return sb.toString().strip();
    }

    private String buildLongMemory(UUID owner, LocalDate day) {
        StringBuilder sb = new StringBuilder();
        LocalDate currentMonthStart = dates.monthStart(day);
        LocalDate prevMonthStart = currentMonthStart.minusMonths(1);

        DiaryPeriod prevMonth = periodService.find(owner, DiaryPeriod.TYPE_MONTH, prevMonthStart).orElse(null);
        boolean monthCovered = false;
        if (prevMonth != null && hasAny(prevMonth)) {
            appendPeriod(sb, "Итог прошлого месяца (" + dates.formatMonth(prevMonthStart) + ")", prevMonth);
            monthCovered = prevMonth.getAutoSummary() != null && !prevMonth.getAutoSummary().isBlank();
        }

        // Completed weeks: of the current month, plus the previous month when it has no summary yet.
        LocalDate weeksFrom = monthCovered ? currentMonthStart : prevMonthStart;
        List<DiaryPeriod> weeks = periodService.findOverlapping(owner, DiaryPeriod.TYPE_WEEK, weeksFrom, day.minusDays(1));
        LocalDate currentWeekStart = dates.weekStart(day);
        for (DiaryPeriod w : weeks) {
            if (!hasAny(w)) continue;
            boolean current = w.getPeriodStart().equals(currentWeekStart);
            String label = (current ? "Текущая неделя (" : "Итог недели (") + dates.formatRange(w.getPeriodStart(), w.getPeriodEnd()) + ")";
            appendPeriod(sb, label, w);
        }
        return sb.toString().strip();
    }

    private void appendPeriod(StringBuilder sb, String label, DiaryPeriod p) {
        if (p.getAutoSummary() != null && !p.getAutoSummary().isBlank()) {
            sb.append(label).append(", автоматически:\n").append(p.getAutoSummary().strip()).append("\n\n");
        }
        if (p.getUserSummary() != null && !p.getUserSummary().isBlank()) {
            sb.append(label).append(", своими словами автора:\n").append(p.getUserSummary().strip()).append("\n\n");
        }
    }

    private boolean hasAny(DiaryPeriod p) {
        return (p.getAutoSummary() != null && !p.getAutoSummary().isBlank())
                || (p.getUserSummary() != null && !p.getUserSummary().isBlank());
    }

    private String buildRecent(UUID owner, LocalDate day) {
        StringBuilder sb = new StringBuilder();
        int recentDays = Math.max(1, llmProperties.diary().recentDays());
        List<Room> rooms = roomRepository.findByOwnerUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(
                owner, day.minusDays(recentDays), day.minusDays(1));
        if (!rooms.isEmpty()) {
            sb.append("Предыдущие дни:\n");
            // newest first: yesterday matters most and survives trimming
            for (int i = rooms.size() - 1; i >= 0; i--) {
                Room r = rooms.get(i);
                String summary = r.getRunningSummary();
                if (summary != null && !summary.isBlank()) {
                    sb.append("— ").append(dates.formatLong(r.getDiaryDate())).append(":\n")
                            .append(summary.strip()).append("\n\n");
                } else if (r.getStatusId() != ROOM_STATUS_ARCHIVED) {
                    String verbatim = recentTurnsVerbatim(r.getId());
                    if (!verbatim.isEmpty()) {
                        sb.append("— ").append(dates.formatLong(r.getDiaryDate()))
                                .append(" (день ещё не подведён, последние реплики дословно):\n")
                                .append(verbatim).append("\n\n");
                    }
                }
            }
        }

        appendMemory(sb, owner, day.minusYears(1), "В этот день год назад");
        appendMemory(sb, owner, day.minusMonths(1), "В этот день месяц назад");
        return sb.toString().strip();
    }

    private void appendMemory(StringBuilder sb, UUID owner, LocalDate date, String label) {
        roomRepository.findByOwnerUserIdAndDiaryDate(owner, date).ifPresent(r -> {
            if (r.getRunningSummary() != null && !r.getRunningSummary().isBlank()) {
                sb.append(label).append(" (").append(dates.formatLong(date)).append("):\n")
                        .append(r.getRunningSummary().strip()).append("\n\n");
            }
        });
    }

    private String recentTurnsVerbatim(UUID roomId) {
        int n = Math.max(1, llmProperties.diary().yesterdayVerbatimTurns());
        List<Turn> turns = turnRepository.findByRoomIdOrderBySeqAsc(roomId);
        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, turns.size() - n);
        for (int i = from; i < turns.size(); i++) {
            Turn t = turns.get(i);
            if (t.getRoleId() == 3) continue;
            sb.append(t.getRoleId() == 2 ? "[Собеседник]: " : "[Автор]: ").append(t.getContent().strip()).append('\n');
        }
        return sb.toString().strip();
    }

    static String trim(String text, int maxChars) {
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(0, maxChars - 1)).strip() + "…";
    }
}
