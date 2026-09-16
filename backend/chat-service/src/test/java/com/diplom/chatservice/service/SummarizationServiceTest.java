package com.diplom.chatservice.service;

import com.diplom.chatservice.config.ChatLlmProperties;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.Turn;
import com.diplom.chatservice.llm.LlmClient;
import com.diplom.chatservice.llm.LlmRequest;
import com.diplom.chatservice.llm.LlmResponse;
import com.diplom.chatservice.repository.RoomParticipantRepository;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.repository.TurnRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummarizationServiceTest {

    @Mock RoomRepository roomRepository;
    @Mock TurnRepository turnRepository;
    @Mock RoomParticipantRepository participantRepository;
    @Mock LlmClient llmClient;
    @Mock ChatLlmProperties llmProperties;
    @Mock RateLimitService rateLimitService;
    @Mock DiaryMemoryIndexer indexer;
    @Mock DiaryMemoryFactService factService;

    private SummarizationService service;
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SummarizationService(roomRepository, turnRepository, participantRepository, llmClient,
                llmProperties, new ObjectMapper(), rateLimitService, indexer, factService);
        lenient().when(llmProperties.prompts()).thenReturn(new ChatLlmProperties.Prompts(
                "", "", "", "PAIRED-SUM", "", "DIARY-DAY", "", "", ""));
        lenient().when(llmProperties.maxOutputTokens()).thenReturn(1200);
        lenient().when(llmProperties.diary()).thenReturn(new ChatLlmProperties.DiaryProps(
                24000, 1500, 120, 600000, 6, 0.3, 2, 7, 40, 6));
        lenient().when(llmProperties.models()).thenReturn(new ChatLlmProperties.Models("", "", ""));
        lenient().when(participantRepository.findByRoomId(any())).thenReturn(List.of());
    }

    private Room room(int typeId, Integer soloMode, int status) {
        return Room.builder().id(UUID.randomUUID()).typeId(typeId).soloModeId(soloMode).statusId(status)
                .ownerUserId(owner).aiModel("m").diaryDate(soloMode != null && soloMode == 2 ? LocalDate.of(2026, 9, 14) : null).build();
    }

    private void stubTurns(Room room) {
        List<Turn> turns = List.of(
                Turn.builder().id(UUID.randomUUID()).roomId(room.getId()).seq(1).roleId(1).content("я устал").build(),
                Turn.builder().id(UUID.randomUUID()).roomId(room.getId()).seq(2).roleId(2).content("от чего?").build());
        when(turnRepository.findByRoomIdOrderBySeqAsc(eq(room.getId()), any(Pageable.class))).thenReturn(new PageImpl<>(turns));
    }

    @Test
    void archivedDiaryDayUsesDiaryPromptAccountsToOwnerAndFeedsMemory() {
        Room room = room(2, 2, 5);
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        stubTurns(room);
        when(llmClient.complete(any())).thenReturn(new LlmResponse("Итог: …", 300, 80, "stop"));

        service.foldTurnsIntoSummary(room.getId(), Integer.MAX_VALUE);

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).complete(captor.capture());
        assertThat(captor.getValue().systemText()).isEqualTo("DIARY-DAY");
        assertThat(captor.getValue().maxOutputTokens()).isEqualTo(1500);
        assertThat(captor.getValue().messages().get(0).content()).contains("[Автор]: я устал").contains("[Собеседник]: от чего?");
        verify(rateLimitService).addDiaryDailyTokens(owner, 380);
        verify(rateLimitService, never()).addDailyTokens(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(indexer).indexDaySummary(room);
        verify(factService).extractFromDay(room.getId());
        assertThat(room.getRunningSummary()).isEqualTo("Итог: …");
        assertThat(room.getSummarizedThroughSeq()).isEqualTo(2);
    }

    @Test
    void midDayOverflowFoldOfActiveDiaryDayDoesNotExtractFacts() {
        Room room = room(2, 2, 3);
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        stubTurns(room);
        when(llmClient.complete(any())).thenReturn(new LlmResponse("Итог", 10, 5, "stop"));

        service.foldTurnsIntoSummary(room.getId(), 1);

        verify(indexer, never()).indexDaySummary(any());
        verify(factService, never()).extractFromDay(any());
        assertThat(room.getSummarizedThroughSeq()).isEqualTo(1);
    }

    @Test
    void pairedRoomKeepsMediationPromptAndPlainSoloIsSkipped() {
        Room paired = room(1, null, 5);
        when(roomRepository.findById(paired.getId())).thenReturn(Optional.of(paired));
        stubTurns(paired);
        when(llmClient.complete(any())).thenReturn(new LlmResponse("Резюме", 10, 5, "stop"));
        service.foldTurnsIntoSummary(paired.getId(), Integer.MAX_VALUE);
        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).complete(captor.capture());
        assertThat(captor.getValue().systemText()).isEqualTo("PAIRED-SUM");
        assertThat(captor.getValue().messages().get(0).content()).contains("[Медиатор]: от чего?");
        verify(rateLimitService).addDailyTokens(owner, 15);

        Room solo = room(2, 1, 5);
        when(roomRepository.findById(solo.getId())).thenReturn(Optional.of(solo));
        service.foldTurnsIntoSummary(solo.getId(), Integer.MAX_VALUE);
        verify(llmClient, org.mockito.Mockito.times(1)).complete(any());
    }
}
