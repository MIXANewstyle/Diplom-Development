-- ============================================================
-- Diary mode: day rooms, week/month periods, memory facts, RAG chunks (pgvector)
-- ============================================================

-- pgvector must be available in the Postgres image (pgvector/pgvector:pg16).
CREATE EXTENSION IF NOT EXISTS vector;

-- ---------- solo mode DIARY ----------
INSERT INTO chat_schema.solo_modes (id, name) VALUES (2, 'DIARY');

-- ---------- rooms: one diary room per owner per local calendar day ----------
ALTER TABLE chat_schema.rooms ADD COLUMN diary_date DATE NULL;

CREATE UNIQUE INDEX uq_rooms_owner_diary_date
    ON chat_schema.rooms (owner_user_id, diary_date)
    WHERE diary_date IS NOT NULL;

COMMENT ON COLUMN chat_schema.rooms.diary_date IS 'Local calendar date of the diary entry (solo_mode_id = 2 only)';

-- ---------- turns: provider-reported cost (OpenRouter usage.cost), NULL when not reported ----------
ALTER TABLE chat_schema.turns ADD COLUMN cost_usd NUMERIC(12,6) NULL;

-- ---------- diary periods (week / month summaries) ----------
CREATE TABLE chat_schema.diary_period_types (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

INSERT INTO chat_schema.diary_period_types (id, name) VALUES
    (1, 'WEEK'),
    (2, 'MONTH');

CREATE TABLE chat_schema.diary_periods (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,  -- soft link to user_schema.users(id)
    period_type_id INT NOT NULL REFERENCES chat_schema.diary_period_types(id),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    auto_summary TEXT NULL,
    auto_summary_through DATE NULL,
    auto_generated_at TIMESTAMP WITH TIME ZONE NULL,
    user_summary TEXT NULL,
    user_updated_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_diary_periods_owner_type_start UNIQUE (owner_user_id, period_type_id, period_start)
);

COMMENT ON COLUMN chat_schema.diary_periods.owner_user_id IS 'Soft link to user_schema.users(id)';

-- ---------- memory facts ("what the AI remembers") ----------
CREATE TABLE chat_schema.diary_fact_categories (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

INSERT INTO chat_schema.diary_fact_categories (id, name) VALUES
    (1, 'PERSON'),
    (2, 'THEME'),
    (3, 'GOAL'),
    (4, 'TRIGGER'),
    (5, 'VALUE'),
    (6, 'OTHER');

CREATE TABLE chat_schema.diary_memory_facts (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,  -- soft link to user_schema.users(id)
    category_id INT NOT NULL REFERENCES chat_schema.diary_fact_categories(id),
    content VARCHAR(300) NOT NULL,
    first_seen_date DATE NOT NULL,
    last_confirmed_date DATE NOT NULL,
    source_room_id UUID NULL REFERENCES chat_schema.rooms(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version INT NOT NULL DEFAULT 0
);

COMMENT ON COLUMN chat_schema.diary_memory_facts.owner_user_id IS 'Soft link to user_schema.users(id)';

CREATE INDEX idx_diary_memory_facts_owner
    ON chat_schema.diary_memory_facts (owner_user_id);

-- ---------- RAG memory chunks (no JPA entity; managed via JDBC) ----------
CREATE TABLE chat_schema.diary_chunk_sources (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

INSERT INTO chat_schema.diary_chunk_sources (id, name) VALUES
    (1, 'USER_TURN'),
    (2, 'DAY_SUMMARY'),
    (3, 'PERIOD_SUMMARY');

CREATE TABLE chat_schema.diary_memory_chunks (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,  -- soft link to user_schema.users(id)
    room_id UUID NULL REFERENCES chat_schema.rooms(id) ON DELETE CASCADE,
    turn_id UUID NULL REFERENCES chat_schema.turns(id) ON DELETE CASCADE,
    period_id UUID NULL REFERENCES chat_schema.diary_periods(id) ON DELETE CASCADE,
    source_id INT NOT NULL REFERENCES chat_schema.diary_chunk_sources(id),
    entry_date DATE NOT NULL,
    chunk_index INT NOT NULL DEFAULT 0,
    content TEXT NOT NULL,
    embedding vector(${embedding_dims}) NOT NULL,
    embedding_model VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

COMMENT ON COLUMN chat_schema.diary_memory_chunks.owner_user_id IS 'Soft link to user_schema.users(id)';

CREATE INDEX idx_diary_chunks_owner_date
    ON chat_schema.diary_memory_chunks (owner_user_id, entry_date);

CREATE INDEX idx_diary_chunks_room
    ON chat_schema.diary_memory_chunks (room_id);

CREATE UNIQUE INDEX uq_diary_chunks_turn
    ON chat_schema.diary_memory_chunks (turn_id, chunk_index)
    WHERE turn_id IS NOT NULL;

-- No HNSW index on purpose: per-user corpora are small and the query is always
-- filtered by owner_user_id; an exact scan with ORDER BY embedding <=> ? is
-- sufficient for MVP. Add HNSW later (pgvector >= 0.8, hnsw.iterative_scan) if needed.
