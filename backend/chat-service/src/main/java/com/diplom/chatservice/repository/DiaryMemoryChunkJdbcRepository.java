package com.diplom.chatservice.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JDBC access to {@code chat_schema.diary_memory_chunks}. The table has no JPA entity on purpose:
 * the {@code vector} column type is not known to Hibernate without an extra module, and every
 * operation on it is naturally SQL (insert with CAST, cosine ORDER BY).
 *
 * <p>The pgvector extension lives in {@code chat_schema} (Flyway runs with that search_path and
 * V7 pins it there), while the application connection's search_path is the default {@code public}.
 * The type and the operator are therefore schema-qualified: {@code chat_schema.vector} and
 * {@code OPERATOR(chat_schema.<=>)}; an unqualified {@code vector} raises "type does not exist".
 */
@Repository
@RequiredArgsConstructor
public class DiaryMemoryChunkJdbcRepository {

    public static final int SOURCE_USER_TURN = 1;
    public static final int SOURCE_DAY_SUMMARY = 2;
    public static final int SOURCE_PERIOD_SUMMARY = 3;

    private final NamedParameterJdbcTemplate jdbc;

    public record ChunkInsert(
            UUID ownerUserId,
            UUID roomId,
            UUID turnId,
            UUID periodId,
            int sourceId,
            LocalDate entryDate,
            int chunkIndex,
            String content,
            float[] embedding,
            String embeddingModel
    ) {}

    public record ChunkHit(
            UUID id,
            int sourceId,
            LocalDate entryDate,
            String content,
            double score
    ) {}

    public record UnindexedTurn(UUID turnId, UUID roomId, UUID ownerUserId, LocalDate entryDate, String content) {}

    public void insertAll(List<ChunkInsert> chunks) {
        if (chunks.isEmpty()) return;
        String sql = """
            INSERT INTO chat_schema.diary_memory_chunks
                (id, owner_user_id, room_id, turn_id, period_id, source_id, entry_date, chunk_index,
                 content, embedding, embedding_model, created_at)
            VALUES (:id, :owner, :roomId, :turnId, :periodId, :sourceId, :entryDate, :chunkIndex,
                    :content, CAST(:embedding AS chat_schema.vector), :model, :createdAt)
            ON CONFLICT DO NOTHING
            """;
        MapSqlParameterSource[] batch = new MapSqlParameterSource[chunks.size()];
        Timestamp now = Timestamp.from(OffsetDateTime.now().toInstant());
        for (int i = 0; i < chunks.size(); i++) {
            ChunkInsert c = chunks.get(i);
            batch[i] = new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("owner", c.ownerUserId())
                    .addValue("roomId", c.roomId())
                    .addValue("turnId", c.turnId())
                    .addValue("periodId", c.periodId())
                    .addValue("sourceId", c.sourceId())
                    .addValue("entryDate", c.entryDate())
                    .addValue("chunkIndex", c.chunkIndex())
                    .addValue("content", c.content())
                    .addValue("embedding", toVectorLiteral(c.embedding()))
                    .addValue("model", c.embeddingModel())
                    .addValue("createdAt", now);
        }
        jdbc.batchUpdate(sql, batch);
    }

    /**
     * Nearest chunks of one owner by cosine similarity, excluding one date (today's entry is
     * already in the prompt verbatim). Exact scan; see the migration note on HNSW.
     */
    public List<ChunkHit> searchTopK(UUID ownerUserId, float[] query, LocalDate excludeDate, int k) {
        String sql = """
            SELECT id, source_id, entry_date, content,
                   1 - (embedding OPERATOR(chat_schema.<=>) CAST(:q AS chat_schema.vector)) AS score
            FROM chat_schema.diary_memory_chunks
            WHERE owner_user_id = :owner
              AND entry_date <> :excludeDate
            ORDER BY embedding OPERATOR(chat_schema.<=>) CAST(:q AS chat_schema.vector)
            LIMIT :k
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("q", toVectorLiteral(query))
                .addValue("owner", ownerUserId)
                .addValue("excludeDate", excludeDate)
                .addValue("k", k);
        return jdbc.query(sql, params, (rs, i) -> new ChunkHit(
                rs.getObject("id", UUID.class),
                rs.getInt("source_id"),
                rs.getObject("entry_date", LocalDate.class),
                rs.getString("content"),
                rs.getDouble("score")));
    }

    public boolean existsForTurn(UUID turnId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_schema.diary_memory_chunks WHERE turn_id = :turnId",
                new MapSqlParameterSource("turnId", turnId), Integer.class);
        return n != null && n > 0;
    }

    public void deleteByPeriodId(UUID periodId) {
        jdbc.update("DELETE FROM chat_schema.diary_memory_chunks WHERE period_id = :periodId",
                new MapSqlParameterSource("periodId", periodId));
    }

    public void deleteDaySummaryChunks(UUID roomId) {
        jdbc.update("DELETE FROM chat_schema.diary_memory_chunks WHERE room_id = :roomId AND source_id = :sourceId",
                new MapSqlParameterSource("roomId", roomId).addValue("sourceId", SOURCE_DAY_SUMMARY));
    }

    /** USER turns of diary rooms that have no chunk yet (catch-up indexing). */
    public List<UnindexedTurn> findUnindexedDiaryUserTurns(int limit) {
        String sql = """
            SELECT t.id AS turn_id, t.room_id, r.owner_user_id, r.diary_date, t.content
            FROM chat_schema.turns t
            JOIN chat_schema.rooms r ON r.id = t.room_id
            WHERE r.solo_mode_id = 2 AND r.diary_date IS NOT NULL AND t.role_id = 1
              AND NOT EXISTS (SELECT 1 FROM chat_schema.diary_memory_chunks c WHERE c.turn_id = t.id)
            ORDER BY t.created_at ASC
            LIMIT :limit
            """;
        return jdbc.query(sql, new MapSqlParameterSource("limit", limit), (rs, i) -> new UnindexedTurn(
                rs.getObject("turn_id", UUID.class),
                rs.getObject("room_id", UUID.class),
                rs.getObject("owner_user_id", UUID.class),
                rs.getObject("diary_date", LocalDate.class),
                rs.getString("content")));
    }

    static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 10 + 2);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    /** Helper for callers that need several inserts from one text split into chunks. */
    public static List<ChunkInsert> forChunks(UUID owner, UUID roomId, UUID turnId, UUID periodId, int sourceId,
                                              LocalDate entryDate, List<String> contents, List<float[]> vectors,
                                              String model) {
        List<ChunkInsert> out = new ArrayList<>(contents.size());
        for (int i = 0; i < contents.size(); i++) {
            out.add(new ChunkInsert(owner, roomId, turnId, periodId, sourceId, entryDate, i,
                    contents.get(i), vectors.get(i), model));
        }
        return out;
    }
}
