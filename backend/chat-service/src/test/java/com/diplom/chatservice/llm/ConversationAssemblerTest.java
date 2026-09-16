package com.diplom.chatservice.llm;

import com.diplom.chatservice.config.ChatLlmProperties;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.RoomParticipant;
import com.diplom.chatservice.entity.Turn;
import com.diplom.chatservice.repository.RoomRepository;
import com.diplom.chatservice.service.DiaryContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationAssemblerTest {

    @Mock ChatLlmProperties llmProperties;
    @Mock RoomRepository roomRepository;
    @Mock DiaryContextService diaryContextService;

    private ConversationAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new ConversationAssembler(llmProperties, new ObjectMapper(), roomRepository, diaryContextService);
        lenient().when(llmProperties.prompts()).thenReturn(new ChatLlmProperties.Prompts(
                "PAIRED\n{context_block}", "SOLO\n{context_block}", "", "SUM",
                "DIARY SYSTEM\n{context_block}", "DAY", "WEEK", "MONTH", "FACTS"));
        lenient().when(llmProperties.promptTokenBudget()).thenReturn(6000);
        lenient().when(llmProperties.maxOutputTokens()).thenReturn(1200);
        lenient().when(llmProperties.temperature()).thenReturn(0.7);
        lenient().when(llmProperties.diary()).thenReturn(new ChatLlmProperties.DiaryProps(
                24000, 1500, 120, 600000, 6, 0.3, 2, 7, 40, 6));
        lenient().when(llmProperties.models()).thenReturn(new ChatLlmProperties.Models("anthropic/claude-sonnet-4.5", "", ""));
    }

    private static Room room(Integer soloMode) {
        return Room.builder().id(UUID.randomUUID()).typeId(2).soloModeId(soloMode).statusId(3)
                .ownerUserId(UUID.randomUUID()).aiModel("m").diaryDate(LocalDate.of(2026, 9, 16)).build();
    }

    private static RoomParticipant solo(UUID roomId) {
        return RoomParticipant.builder().id(UUID.randomUUID()).roomId(roomId).roleId(3)
                .contextSnapshot("{\"displayName\":\"Артём\",\"about\":\"о себе\"}").build();
    }

    private static Turn turn(UUID roomId, int seq, int role, String text) {
        return Turn.builder().id(UUID.randomUUID()).roomId(roomId).seq(seq).roleId(role).content(text).build();
    }

    @Test
    void diaryRequestPutsRagIntoLastUserMessageAndMarksCacheBoundaries() {
        Room room = room(2);
        when(diaryContextService.buildContextBlock(any(), any(), any())).thenReturn("CTX");
        List<Turn> turns = List.of(
                turn(room.getId(), 1, 1, "первое"),
                turn(room.getId(), 2, 2, "ответ"),
                turn(room.getId(), 3, 1, "второе"));

        LlmRequest req = assembler.assemble(room, List.of(solo(room.getId())), turns, "[RAG]");

        assertThat(req.model()).isEqualTo("anthropic/claude-sonnet-4.5");
        assertThat(req.maxOutputTokens()).isEqualTo(1500);
        assertThat(req.system()).hasSize(1);
        assertThat(req.system().get(0).cacheBoundary()).isTrue();
        assertThat(req.system().get(0).text()).isEqualTo("DIARY SYSTEM\nCTX");

        List<LlmMessage> msgs = req.messages();
        assertThat(msgs).hasSize(3);
        assertThat(msgs.get(0).content()).isEqualTo("первое");
        assertThat(msgs.get(0).cacheBoundary()).isFalse();
        assertThat(msgs.get(1).cacheBoundary()).isTrue();          // last assistant before the current user turn
        assertThat(msgs.get(2).content()).startsWith("[RAG]\n\nЗапись:\nвторое");
        assertThat(msgs.get(2).cacheBoundary()).isFalse();
        assertThat(req.hasCacheBoundaries()).isTrue();
    }

    @Test
    void diaryWithoutRagLeavesUserMessageUntouched() {
        Room room = room(2);
        when(diaryContextService.buildContextBlock(any(), any(), any())).thenReturn("CTX");
        LlmRequest req = assembler.assemble(room, List.of(solo(room.getId())), List.of(turn(room.getId(), 1, 1, "привет")), null);
        assertThat(req.messages().get(0).content()).isEqualTo("привет");
    }

    @Test
    void soloRoomIsUnchangedNoBoundariesDefaultModel() {
        Room room = room(1);
        LlmRequest req = assembler.assemble(room, List.of(solo(room.getId())), List.of(turn(room.getId(), 1, 1, "привет")));

        assertThat(req.model()).isNull();
        assertThat(req.maxOutputTokens()).isEqualTo(1200);
        assertThat(req.hasCacheBoundaries()).isFalse();
        assertThat(req.systemText()).startsWith("SOLO\nТип комнаты: solo. Режим: PROBLEM_SOLVING.");
        assertThat(req.systemText()).contains("Участник: Артём. О себе: о себе.");
        assertThat(req.messages().get(0).content()).isEqualTo("привет");
    }
}
