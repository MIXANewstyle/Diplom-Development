package com.diplom.chatservice.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DiaryDateServiceTest {

    private static DiaryDateService at(String utcInstant) {
        return new DiaryDateService(Clock.fixed(Instant.parse(utcInstant), ZoneOffset.UTC));
    }

    @Test
    void todayIsUtcDate() {
        assertThat(at("2026-09-16T23:30:00Z").today()).isEqualTo(LocalDate.of(2026, 9, 16));
    }

    @Test
    void writableWindowIsTodayPlusMinusOneDay() {
        DiaryDateService d = at("2026-09-16T12:00:00Z");
        assertThat(d.isWritable(LocalDate.of(2026, 9, 15))).isTrue();   // yesterday (UTC)
        assertThat(d.isWritable(LocalDate.of(2026, 9, 16))).isTrue();   // today
        assertThat(d.isWritable(LocalDate.of(2026, 9, 17))).isTrue();   // tomorrow for UTC+ clients
        assertThat(d.isWritable(LocalDate.of(2026, 9, 14))).isFalse();
        assertThat(d.isWritable(LocalDate.of(2026, 9, 18))).isFalse();
    }

    @Test
    void closeThresholdNeverOverlapsWritableWindow() {
        DiaryDateService d = at("2026-09-16T12:00:00Z");
        LocalDate threshold = d.closeThreshold();
        assertThat(threshold).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(d.isWritable(threshold)).isFalse();
        assertThat(d.isWritable(threshold.plusDays(1))).isTrue();
    }

    @Test
    void periodDueOnlyAfterEveryDayCouldBeClosedAndSummarized() {
        DiaryDateService d = at("2026-09-16T12:00:00Z");
        assertThat(d.isPeriodDue(LocalDate.of(2026, 9, 13))).isTrue();
        assertThat(d.isPeriodDue(LocalDate.of(2026, 9, 14))).isFalse();
    }

    @Test
    void isoWeeksAcrossYearBoundary() {
        DiaryDateService d = at("2026-01-02T12:00:00Z");
        // 1 Jan 2026 is a Thursday → ISO week starts Mon 29 Dec 2025
        assertThat(d.weekStart(LocalDate.of(2026, 1, 1))).isEqualTo(LocalDate.of(2025, 12, 29));
        assertThat(d.weekEnd(LocalDate.of(2026, 1, 1))).isEqualTo(LocalDate.of(2026, 1, 4));
        assertThat(d.weekStart(LocalDate.of(2025, 12, 29))).isEqualTo(LocalDate.of(2025, 12, 29));
        assertThat(d.formatRange(LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 4)))
                .isEqualTo("29 декабря 2025 – 4 января 2026");
    }

    @Test
    void monthBoundsHandleLeapFebruary() {
        DiaryDateService d = at("2028-02-10T12:00:00Z");
        assertThat(d.monthStart(LocalDate.of(2028, 2, 10))).isEqualTo(LocalDate.of(2028, 2, 1));
        assertThat(d.monthEnd(LocalDate.of(2028, 2, 10))).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(d.formatRange(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29))).isEqualTo("1–29 февраля 2028");
    }
}
