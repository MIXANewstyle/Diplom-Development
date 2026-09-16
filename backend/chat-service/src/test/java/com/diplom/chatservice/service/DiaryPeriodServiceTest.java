package com.diplom.chatservice.service;

import com.diplom.chatservice.config.ChatLlmProperties;
import com.diplom.chatservice.entity.DiaryPeriod;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.llm.LlmClient;
import com.diplom.chatservice.llm.LlmRequest;
import com.diplom.chatservice.llm.LlmResponse;
import com.diplom.chatservice.repository.DiaryPeriodRepository;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.repository.TurnRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryPeriodServiceTest {

    @Mock DiaryPeriodRepository periodRepository;
    @Mock RoomRepository roomRepository;
    @Mock TurnRepository turnRepository;
    @Mock LlmClient llmClient;
    @Mock ChatLlmProperties llmProperties;
    @Mock DiaryMemoryIndexer indexer;
    @Mock RateLimitService rateLimitService;

    private final UUID owner = UUID.randomUUID();
    private DiaryPeriodService service;

    @BeforeEach
    void setUp() {
        DiaryDateService dates = new DiaryDateService(Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC));
        service = new DiaryPeriodService(periodRepository, roomRepository, turnRepository, llmClient, llmProperties, dates, indexer, rateLimitService);
        lenient().when(llmProperties.prompts()).thenReturn(new ChatLlmProperties.Prompts(
                "", "", "", "", "", "DAY", "WEEK-PROMPT", "MONTH-PROMPT", ""));
        lenient().when(llmProperties.diary()).thenReturn(new ChatLlmProperties.DiaryProps(
                24000, 1500, 120, 600000, 6, 0.3, 2, 7, 40, 6));
        lenient().when(llmProperties.models()).thenReturn(new ChatLlmProperties.Models("", "openai/gpt-4.1-mini", ""));
        lenient().when(periodRepository.save(any())).thenAnswer(inv -> {
            DiaryPeriod p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });
    }

    private Room day(LocalDate date, String summary) {
        return Room.builder().id(UUID.randomUUID()).ownerUserId(owner).typeId(2).soloModeId(2).statusId(5)
                .aiModel("m").diaryDate(date).runningSummary(summary).build();
    }

    @Test
    void weekSummaryIsBuiltFromDaySummariesAndMarkedCompleteWhenClosed() {
        LocalDate start = LocalDate.of(2026, 9, 7);
        when(roomRepository.findByOwnerUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(owner, start, LocalDate.of(2026, 9, 13)))
                .thenReturn(List.of(day(LocalDate.of(2026, 9, 8), "ИТОГ-8"), day(LocalDate.of(2026, 9, 10), null), day(LocalDate.of(2026, 9, 12), "ИТОГ-12")));
        when(periodRepository.findByOwnerUserIdAndPeriodTypeIdAndPeriodStart(owner, DiaryPeriod.TYPE_WEEK, start)).thenReturn(Optional.empty());
        when(periodRepository.findById(any())).thenAnswer(inv -> Optional.of(DiaryPeriod.builder().id(inv.getArgument(0)).ownerUserId(owner)
                .periodTypeId(1).periodStart(start).periodEnd(start.plusDays(6)).build()));
        when(llmClient.complete(any())).thenReturn(new LlmResponse("НЕДЕЛЯ", 100, 50, "stop"));

        DiaryPeriod result = service.generateAutoSummary(owner, DiaryPeriod.TYPE_WEEK, start);

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).complete(captor.capture());
        LlmRequest req = captor.getValue();
        assertThat(req.systemText()).isEqualTo("WEEK-PROMPT");
        assertThat(req.model()).isEqualTo("openai/gpt-4.1-mini");
        String input = req.messages().get(0).content();
        assertThat(input).startsWith("Неделя: 7–13 сентября 2026");
        assertThat(input).contains("— 8 сентября 2026:\nИТОГ-8").contains("— 12 сентября 2026:\nИТОГ-12");
        assertThat(input).doesNotContain("10 сентября");

        assertThat(result.getAutoSummary()).isEqualTo("НЕДЕЛЯ");
        assertThat(result.getAutoSummaryThrough()).isEqualTo(LocalDate.of(2026, 9, 13)); // closed period → through = end
        verify(rateLimitService).addDiaryDailyTokens(owner, 150);
        verify(indexer).indexPeriodSummary(result);
    }

    @Test
    void currentWeekSummaryIsPartialThroughLastSummarizedDay() {
        LocalDate start = LocalDate.of(2026, 9, 14);
        when(roomRepository.findByOwnerUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(owner, start, LocalDate.of(2026, 9, 16)))
                .thenReturn(List.of(day(LocalDate.of(2026, 9, 14), "ИТОГ-14")));
        when(periodRepository.findByOwnerUserIdAndPeriodTypeIdAndPeriodStart(owner, DiaryPeriod.TYPE_WEEK, start)).thenReturn(Optional.empty());
        when(periodRepository.findById(any())).thenAnswer(inv -> Optional.of(DiaryPeriod.builder().id(inv.getArgument(0)).ownerUserId(owner)
                .periodTypeId(1).periodStart(start).periodEnd(start.plusDays(6)).build()));
        when(llmClient.complete(any())).thenReturn(new LlmResponse("ЧАСТЬ", 10, 5, "stop"));

        DiaryPeriod result = service.generateAutoSummary(owner, DiaryPeriod.TYPE_WEEK, start);

        assertThat(result.getAutoSummaryThrough()).isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    void nothingToSummarizeReturnsNullWithoutLlmCall() {
        LocalDate start = LocalDate.of(2026, 9, 7);
        when(roomRepository.findByOwnerUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(eq(owner), eq(start), any()))
                .thenReturn(List.of(day(LocalDate.of(2026, 9, 8), null)));

        assertThat(service.generateAutoSummary(owner, DiaryPeriod.TYPE_WEEK, start)).isNull();
        verify(llmClient, org.mockito.Mockito.never()).complete(any());
    }

    @Test
    void monthInputIncludesAuthorsWeekTexts() {
        LocalDate start = LocalDate.of(2026, 8, 1);
        when(roomRepository.findByOwnerUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(owner, start, LocalDate.of(2026, 8, 31)))
                .thenReturn(List.of(day(LocalDate.of(2026, 8, 3), "ИТОГ-3")));
        DiaryPeriod month = DiaryPeriod.builder().id(UUID.randomUUID()).ownerUserId(owner).periodTypeId(2)
                .periodStart(start).periodEnd(LocalDate.of(2026, 8, 31)).userSummary("МЕСЯЦ-СВОИМИ").build();
        when(periodRepository.findByOwnerUserIdAndPeriodTypeIdAndPeriodStart(owner, DiaryPeriod.TYPE_MONTH, start)).thenReturn(Optional.of(month));
        when(periodRepository.findById(month.getId())).thenReturn(Optional.of(month));
        when(periodRepository.findByOwnerUserIdAndPeriodTypeIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualOrderByPeriodStartAsc(
                eq(owner), eq(DiaryPeriod.TYPE_WEEK), any(), any()))
                .thenReturn(List.of(DiaryPeriod.builder().ownerUserId(owner).periodTypeId(1).periodStart(LocalDate.of(2026, 8, 3))
                        .periodEnd(LocalDate.of(2026, 8, 9)).userSummary("НЕДЕЛЯ-СВОИМИ").build()));
        when(llmClient.complete(any())).thenReturn(new LlmResponse("МЕСЯЦ", 10, 5, "stop"));

        service.generateAutoSummary(owner, DiaryPeriod.TYPE_MONTH, start);

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).complete(captor.capture());
        String input = captor.getValue().messages().get(0).content();
        assertThat(captor.getValue().systemText()).isEqualTo("MONTH-PROMPT");
        assertThat(input).contains("Итоги недель своими словами автора:").contains("НЕДЕЛЯ-СВОИМИ");
        assertThat(input).contains("Итог периода своими словами автора:\nМЕСЯЦ-СВОИМИ");
    }

}
