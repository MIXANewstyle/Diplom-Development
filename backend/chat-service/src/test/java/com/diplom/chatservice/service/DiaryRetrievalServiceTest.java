package com.diplom.chatservice.service;

import com.diplom.chatservice.config.ChatLlmProperties;
import com.diplom.chatservice.repository.DiaryMemoryChunkJdbcRepository;
import com.diplom.chatservice.repository.DiaryMemoryChunkJdbcRepository.ChunkHit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryRetrievalServiceTest {

    @Mock DiaryMemoryChunkJdbcRepository chunkRepository;
    @Mock ChatLlmProperties llmProperties;

    private DiaryRetrievalService service() {
        return new DiaryRetrievalService(chunkRepository, llmProperties, new DiaryDateService());
    }

    private static ChunkHit hit(String date, double score, String content) {
        return new ChunkHit(UUID.randomUUID(), DiaryMemoryChunkJdbcRepository.SOURCE_USER_TURN, LocalDate.parse(date), content, score);
    }

    @Test
    void selectAppliesThresholdPerDayCapAndTopKThenSortsChronologically() {
        List<ChunkHit> hits = List.of(
                hit("2026-03-12", 0.9, "a"),
                hit("2026-03-12", 0.8, "b"),
                hit("2026-03-12", 0.7, "c"),   // third of the same day — dropped by maxPerDay=2
                hit("2026-01-05", 0.6, "d"),
                hit("2026-02-20", 0.2, "e"),   // below threshold
                hit("2026-04-01", 0.5, "f"),
                hit("2026-04-02", 0.45, "g")   // beyond topK=4
        );
        List<ChunkHit> selected = service().select(hits, 0.3, 4, 2);

        assertThat(selected).extracting(ChunkHit::content).containsExactly("d", "a", "b", "f");
    }

    @Test
    void buildsBlockWithDatesAndReturnsNullWhenNothingRelevant() {
        when(llmProperties.diary()).thenReturn(new ChatLlmProperties.DiaryProps(
                24000, 1500, 120, 600000, 6, 0.3, 2, 7, 40, 6));
        LocalDate today = LocalDate.of(2026, 9, 16);
        when(chunkRepository.searchTopK(any(), any(), eq(today), anyInt()))
                .thenReturn(List.of(hit("2026-03-12", 0.8, "мне страшно, что она уйдёт")));

        String block = service().buildRagBlock(UUID.randomUUID(), today, new float[]{0.1f}, 8000);

        assertThat(block).startsWith(DiaryRetrievalService.RAG_HEADER);
        assertThat(block).contains("12 марта 2026 (запись): «мне страшно, что она уйдёт»");
        assertThat(block).endsWith(DiaryRetrievalService.RAG_FOOTER);

        when(chunkRepository.searchTopK(any(), any(), eq(today), anyInt())).thenReturn(List.of(hit("2026-03-12", 0.1, "x")));
        assertThat(service().buildRagBlock(UUID.randomUUID(), today, new float[]{0.1f}, 8000)).isNull();
    }

    @Test
    void blockIsTrimmedToMaxChars() {
        when(llmProperties.diary()).thenReturn(new ChatLlmProperties.DiaryProps(
                24000, 1500, 120, 600000, 6, 0.3, 2, 7, 40, 6));
        LocalDate today = LocalDate.of(2026, 9, 16);
        when(chunkRepository.searchTopK(any(), any(), eq(today), anyInt())).thenReturn(List.of(
                hit("2026-03-12", 0.9, "a".repeat(300)),
                hit("2026-03-13", 0.8, "b".repeat(300))));

        String block = service().buildRagBlock(UUID.randomUUID(), today, new float[]{0.1f}, 450);

        assertThat(block.length()).isLessThanOrEqualTo(450 + DiaryRetrievalService.RAG_FOOTER.length());
        assertThat(block).endsWith(DiaryRetrievalService.RAG_FOOTER);
    }
}
