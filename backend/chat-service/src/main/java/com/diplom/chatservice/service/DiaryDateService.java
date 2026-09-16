package com.diplom.chatservice.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Calendar rules of the diary. All server-side "today" decisions are made in UTC; the client sends
 * its own local date and the ±1-day window covers every timezone (UTC−12 … UTC+14).
 *
 * <ul>
 *   <li>writable: {@code today-1 <= date <= today+1} ("today and yesterday" for every timezone)</li>
 *   <li>close: a day room is archived once {@code date <= today-2} — never while still writable</li>
 *   <li>period due: week/month summaries are generated once {@code periodEnd <= today-3}, i.e. after
 *       every day of the period has been closed and had time to be summarized</li>
 * </ul>
 */
@Service
public class DiaryDateService {

    private static final DateTimeFormatter LONG_RU = DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("ru"));
    private static final DateTimeFormatter SHORT_RU = DateTimeFormatter.ofPattern("d MMMM", new Locale("ru"));
    private static final DateTimeFormatter MONTH_RU = DateTimeFormatter.ofPattern("LLLL yyyy", new Locale("ru"));

    private final Clock clock;

    public DiaryDateService() {
        this(Clock.systemUTC());
    }

    public DiaryDateService(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    public boolean isWritable(LocalDate date) {
        LocalDate today = today();
        return !date.isBefore(today.minusDays(1)) && !date.isAfter(today.plusDays(1));
    }

    /** Diary rooms dated at or before this date may be archived. */
    public LocalDate closeThreshold() {
        return today().minusDays(2);
    }

    /** True when every day of a period ending on {@code periodEnd} has been closed and summarized. */
    public boolean isPeriodDue(LocalDate periodEnd) {
        return !periodEnd.isAfter(today().minusDays(3));
    }

    public LocalDate weekStart(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    public LocalDate weekEnd(LocalDate date) {
        return weekStart(date).plusDays(6);
    }

    public LocalDate monthStart(LocalDate date) {
        return date.withDayOfMonth(1);
    }

    public LocalDate monthEnd(LocalDate date) {
        return YearMonth.from(date).atEndOfMonth();
    }

    public String formatLong(LocalDate date) {
        return date.format(LONG_RU);
    }

    public String formatShort(LocalDate date) {
        return date.format(SHORT_RU);
    }

    public String formatMonth(LocalDate date) {
        return date.format(MONTH_RU);
    }

    /** "1–7 сентября 2026" or "29 декабря 2025 – 4 января 2026". */
    public String formatRange(LocalDate from, LocalDate to) {
        if (from.getYear() == to.getYear() && from.getMonth() == to.getMonth()) {
            return from.getDayOfMonth() + "–" + to.format(LONG_RU);
        }
        return from.format(LONG_RU) + " – " + to.format(LONG_RU);
    }
}
