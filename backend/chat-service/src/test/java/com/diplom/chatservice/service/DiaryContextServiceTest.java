package com.diplom.chatservice.service;

import com.diplom.chatservice.config.ChatLlmProperties;
import com.diplom.chatservice.entity.DiaryPeriod;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.Turn;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.repository.TurnRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryContextServiceTest {

    @Mock RoomRepository roomRepository;
    @Mock TurnRepository turnRepository;
    @Mock DiaryPeriodService periodService;
    @Mock DiaryMemoryFactService factService;
    @Mock ChatLlmProperties llmProperties;

    private final UUID owner = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 9, 16); // Wednesday
    private DiaryContextService service;

    @BeforeEach
    void setUp() {
        service = new DiaryContextService(roomRepository, turnRepository, periodService, factService, new DiaryDateService(), llmProperties);
        lenient().when(llmProperties.diary()).thenReturn(new ChatLlmProperties.DiaryProps(
                24000, 1500, 120, 600000, 6, 0.3, 2, 7, 40, 6));
        lenient().when(roomRepository.findByOwnerUserIdAndDiaryDate(any(), any())).thenReturn(Optional.empty());
        lenient().when(roomRepository.findByOwnerUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(any(), any(), any())).thenReturn(List.of());
        lenient().when(periodService.find(any(), anyInt(), any())).thenReturn(Optional.empty());
        lenient().when(periodService.findOverlapping(any(), anyInt(), any(), any())).thenReturn(List.of());
    }

    private Room day(LocalDate date, String summary, int status) {
        return Room.builder().id(UUID.randomUUID()).ownerUserId(owner).typeId(2).soloModeId(2).statusId(status)
                .aiModel("m").diaryDate(date).runningSummary(summary).build();
    }

    @Test
    void layersAppearInStableOrderPersonaThenLongMemoryThenRecent() {
        when(factService.buildFactsBlock(eq(owner), anyInt())).thenReturn("Что известно об авторе:\nЛюди:\n- Лера — партнёрша");
        DiaryPeriod prevMonth = DiaryPeriod.builder().ownerUserId(owner).periodTypeId(2)
                .periodStart(LocalDate.of(2026, 8, 1)).periodEnd(LocalDate.of(2026, 8, 31))
                .autoSummary("АВГУСТ-АВТО").userSummary("АВГУСТ-СВОИМИ").build();
        when(periodService.find(owner, DiaryPeriod.TYPE_MONTH, LocalDate.of(2026, 8, 1))).thenReturn(Optional.of(prevMonth));
        DiaryPeriod week = DiaryPeriod.builder().ownerUserId(owner).periodTypeId(1)
                .periodStart(LocalDate.of(2026, 9, 7)).periodEnd(LocalDate.of(2026, 9, 13)).autoSummary("НЕДЕЛЯ-АВТО").build();
        when(periodService.findOverlapping(owner, DiaryPeriod.TYPE_WEEK, LocalDate.of(2026, 9, 1), today.minusDays(1))).thenReturn(List.of(week));
        when(roomRepository.findByOwnerUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(owner, today.minusDays(7), today.minusDays(1)))
                .thenReturn(List.of(day(LocalDate.of(2026, 9, 14), "ИТОГ-14", 5), day(LocalDate.of(2026, 9, 15), "ИТОГ-15", 5)));
        when(roomRepository.findByOwnerUserIdAndDiaryDate(owner, today.minusYears(1)))
                .thenReturn(Optional.of(day(today.minusYears(1), "ГОД-НАЗАД", 5)));

        String ctx = service.buildContextBlock(day(today, null, 3), "Артём", "о себе");

        assertThat(ctx).startsWith("Тип комнаты: дневник. Дата записи: 16 сентября 2026 (среда).\nАвтор: Артём. О себе: о себе.");
        int persona = ctx.indexOf("Лера — партнёрша");
        int month = ctx.indexOf("АВГУСТ-АВТО");
        int monthOwn = ctx.indexOf("АВГУСТ-СВОИМИ");
        int weekIdx = ctx.indexOf("НЕДЕЛЯ-АВТО");
        int d15 = ctx.indexOf("ИТОГ-15");
        int d14 = ctx.indexOf("ИТОГ-14");
        int year = ctx.indexOf("ГОД-НАЗАД");
        assertThat(persona).isPositive();
        assertThat(month).isGreaterThan(persona);
        assertThat(monthOwn).isGreaterThan(month);
        assertThat(weekIdx).isGreaterThan(monthOwn);
        assertThat(d15).isGreaterThan(weekIdx);        // recent days newest first
        assertThat(d14).isGreaterThan(d15);
        assertThat(year).isGreaterThan(d14);
        assertThat(ctx).contains("Итог прошлого месяца (август 2026), автоматически:");
        assertThat(ctx).contains("Итог недели (7–13 сентября 2026), автоматически:");
        assertThat(ctx).contains("В этот день год назад (16 сентября 2025):");
    }

    @Test
    void yesterdayWithoutSummaryFallsBackToVerbatimTurns() {
        Room yesterday = day(today.minusDays(1), null, 3);
        when(roomRepository.findByOwnerUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(owner, today.minusDays(7), today.minusDays(1)))
                .thenReturn(List.of(yesterday));
        when(turnRepository.findByRoomIdOrderBySeqAsc(yesterday.getId())).thenReturn(List.of(
                Turn.builder().roomId(yesterday.getId()).seq(1).roleId(1).content("вчера было тяжело").build(),
                Turn.builder().roomId(yesterday.getId()).seq(2).roleId(2).content("что именно?").build()));

        String ctx = service.buildContextBlock(day(today, null, 3), null, null);

        assertThat(ctx).contains("Автор: Автор. О себе: не указано.");
        assertThat(ctx).contains("15 сентября 2026 (день ещё не подведён, последние реплики дословно):");
        assertThat(ctx).contains("[Автор]: вчера было тяжело");
        assertThat(ctx).contains("[Собеседник]: что именно?");
    }

    @Test
    void weeksOfPreviousMonthAreIncludedWhenMonthHasNoSummaryYet() {
        DiaryPeriod week = DiaryPeriod.builder().ownerUserId(owner).periodTypeId(1)
                .periodStart(LocalDate.of(2026, 8, 24)).periodEnd(LocalDate.of(2026, 8, 30)).autoSummary("АВГ-НЕДЕЛЯ").build();
        when(periodService.findOverlapping(owner, DiaryPeriod.TYPE_WEEK, LocalDate.of(2026, 8, 1), today.minusDays(1))).thenReturn(List.of(week));

        String ctx = service.buildContextBlock(day(today, null, 3), "А", null);

        assertThat(ctx).contains("АВГ-НЕДЕЛЯ");
    }

    @Test
    void eachLayerIsTrimmedToItsOwnBudget() {
        String huge = "x".repeat(20000);
        when(roomRepository.findByOwnerUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(owner, today.minusDays(7), today.minusDays(1)))
                .thenReturn(List.of(day(today.minusDays(1), huge, 5)));

        String ctx = service.buildContextBlock(day(today, null, 3), "А", null);

        assertThat(ctx.length()).isLessThan(DiaryContextService.RECENT_TOKENS * 4 + 300);
        assertThat(ctx).endsWith("…");
    }
}
