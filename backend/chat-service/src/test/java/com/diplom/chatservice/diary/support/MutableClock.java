package com.diplom.chatservice.diary.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock the test moves by hand; the diary's "today" follows it. */
public final class MutableClock extends Clock {

    private volatile Instant instant = Instant.parse("2026-01-01T12:00:00Z");

    public void set(LocalDate date) {
        this.instant = date.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC);
    }

    public LocalDate today() {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this; // always UTC, matching DiaryDateService
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
