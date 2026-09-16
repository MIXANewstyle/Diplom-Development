package com.diplom.chatservice.service;

import com.diplom.chatservice.entity.DiaryPeriod;
import com.diplom.chatservice.entity.Room;
import com.diplom.chatservice.entity.Turn;
import com.diplom.chatservice.llm.EmbeddingClient;
import com.diplom.chatservice.llm.TextChunker;
import com.diplom.chatservice.repository.DiaryMemoryChunkJdbcRepository;
import com.diplom.chatservice.repository.DiaryMemoryChunkJdbcRepository.ChunkInsert;
import com.diplom.chatservice.repository.DiaryMemoryChunkJdbcRepository.UnindexedTurn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes diary text into the RAG store. Every method is best-effort: a failure is logged and the
 * periodic {@link #catchUp(int)} pass (run by {@code DiarySweepService}) re-indexes anything missing.
 *
 * <p>§16.4: never log diary content, only ids and sizes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryMemoryIndexer {

    private static final int CHUNK_MAX_CHARS = 1500;

    private final DiaryMemoryChunkJdbcRepository chunkRepository;
    private final EmbeddingClient embeddingClient;

    private final TextChunker chunker = new TextChunker(CHUNK_MAX_CHARS);

    /**
     * Index one USER turn. When the caller already embedded the text (the retrieval step embeds the
     * same message), {@code precomputed} avoids a second embedding call; it is used only if the turn
     * fits in a single chunk.
     */
    public void indexUserTurn(Room room, Turn turn, float[] precomputed) {
        try {
            List<String> chunks = chunker.chunk(turn.getContent());
            if (chunks.isEmpty()) return;
            List<float[]> vectors;
            if (precomputed != null && chunks.size() == 1) {
                vectors = List.of(precomputed);
            } else {
                vectors = embeddingClient.embed(chunks);
            }
            chunkRepository.insertAll(DiaryMemoryChunkJdbcRepository.forChunks(
                    room.getOwnerUserId(), room.getId(), turn.getId(), null,
                    DiaryMemoryChunkJdbcRepository.SOURCE_USER_TURN, room.getDiaryDate(),
                    chunks, vectors, embeddingClient.modelName()));
        } catch (Exception e) {
            log.warn("Diary indexing failed for turn {} (room {}): {}", turn.getId(), room.getId(), e.getMessage());
        }
    }

    /** Re-index the day summary (replaces a previous version). */
    public void indexDaySummary(Room room) {
        if (room.getRunningSummary() == null || room.getRunningSummary().isBlank() || room.getDiaryDate() == null) return;
        try {
            List<String> chunks = chunker.chunk(room.getRunningSummary());
            List<float[]> vectors = embeddingClient.embed(chunks);
            chunkRepository.deleteDaySummaryChunks(room.getId());
            chunkRepository.insertAll(DiaryMemoryChunkJdbcRepository.forChunks(
                    room.getOwnerUserId(), room.getId(), null, null,
                    DiaryMemoryChunkJdbcRepository.SOURCE_DAY_SUMMARY, room.getDiaryDate(),
                    chunks, vectors, embeddingClient.modelName()));
        } catch (Exception e) {
            log.warn("Diary indexing failed for day summary of room {}: {}", room.getId(), e.getMessage());
        }
    }

    /** Re-index a period auto-summary (replaces a previous version). */
    public void indexPeriodSummary(DiaryPeriod period) {
        if (period.getAutoSummary() == null || period.getAutoSummary().isBlank()) return;
        try {
            List<String> chunks = chunker.chunk(period.getAutoSummary());
            List<float[]> vectors = embeddingClient.embed(chunks);
            chunkRepository.deleteByPeriodId(period.getId());
            chunkRepository.insertAll(DiaryMemoryChunkJdbcRepository.forChunks(
                    period.getOwnerUserId(), null, null, period.getId(),
                    DiaryMemoryChunkJdbcRepository.SOURCE_PERIOD_SUMMARY, period.getPeriodEnd(),
                    chunks, vectors, embeddingClient.modelName()));
        } catch (Exception e) {
            log.warn("Diary indexing failed for period {}: {}", period.getId(), e.getMessage());
        }
    }

    /** Index USER turns that have no chunks yet. Returns the number of turns processed. */
    public int catchUp(int limit) {
        List<UnindexedTurn> pending;
        try {
            pending = chunkRepository.findUnindexedDiaryUserTurns(limit);
        } catch (Exception e) {
            log.warn("Diary index catch-up query failed: {}", e.getMessage());
            return 0;
        }
        int done = 0;
        for (UnindexedTurn t : pending) {
            try {
                List<String> chunks = chunker.chunk(t.content());
                if (chunks.isEmpty()) continue;
                List<float[]> vectors = embeddingClient.embed(chunks);
                List<ChunkInsert> inserts = new ArrayList<>(DiaryMemoryChunkJdbcRepository.forChunks(
                        t.ownerUserId(), t.roomId(), t.turnId(), null,
                        DiaryMemoryChunkJdbcRepository.SOURCE_USER_TURN, t.entryDate(),
                        chunks, vectors, embeddingClient.modelName()));
                chunkRepository.insertAll(inserts);
                done++;
            } catch (Exception e) {
                log.warn("Diary index catch-up failed for turn {}: {}", t.turnId(), e.getMessage());
                // an embedding-provider outage would fail every item; stop and retry next sweep
                break;
            }
        }
        if (done > 0) {
            log.info("Diary index catch-up indexed {} turn(s)", done);
        }
        return done;
    }
}
