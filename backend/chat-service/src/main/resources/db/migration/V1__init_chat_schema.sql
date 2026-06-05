CREATE SCHEMA IF NOT EXISTS chat_schema;

-- ============================
-- Dictionary tables (§15.1)
-- ============================

CREATE TABLE chat_schema.room_types (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

INSERT INTO chat_schema.room_types (id, name) VALUES
    (1, 'PAIRED'),
    (2, 'SOLO');

CREATE TABLE chat_schema.room_statuses (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

INSERT INTO chat_schema.room_statuses (id, name) VALUES
    (1, 'CREATED'),
    (2, 'WAITING_CONSENT'),
    (3, 'ACTIVE'),
    (4, 'ENDING'),
    (5, 'ARCHIVED'),
    (6, 'ABANDONED'),
    (7, 'EXPIRED');

CREATE TABLE chat_schema.participant_roles (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

INSERT INTO chat_schema.participant_roles (id, name) VALUES
    (1, 'INITIATOR'),
    (2, 'INVITEE'),
    (3, 'SOLO');

CREATE TABLE chat_schema.turn_roles (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

INSERT INTO chat_schema.turn_roles (id, name) VALUES
    (1, 'USER'),
    (2, 'ASSISTANT'),
    (3, 'SYSTEM');

CREATE TABLE chat_schema.solo_modes (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

INSERT INTO chat_schema.solo_modes (id, name) VALUES
    (1, 'PROBLEM_SOLVING');

CREATE TABLE chat_schema.invite_statuses (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

INSERT INTO chat_schema.invite_statuses (id, name) VALUES
    (1, 'ACTIVE'),
    (2, 'CONSUMED'),
    (3, 'REVOKED'),
    (4, 'EXPIRED');

-- ============================
-- Main tables (§15.2)
-- ============================

CREATE TABLE chat_schema.rooms (
    id UUID PRIMARY KEY,
    type_id INT NOT NULL REFERENCES chat_schema.room_types(id),
    solo_mode_id INT NULL REFERENCES chat_schema.solo_modes(id),
    status_id INT NOT NULL REFERENCES chat_schema.room_statuses(id),
    owner_user_id UUID NOT NULL,  -- soft link to user_schema.users(id)
    current_floor_participant_id UUID NULL,  -- FK added after room_participants exists
    phase VARCHAR(20) NULL,
    ai_model VARCHAR(100) NOT NULL,
    seed_context_room_id UUID NULL REFERENCES chat_schema.rooms(id),
    running_summary TEXT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NULL,
    ended_at TIMESTAMP WITH TIME ZONE NULL,
    version INT NOT NULL DEFAULT 0
);

COMMENT ON COLUMN chat_schema.rooms.owner_user_id IS 'Soft link to user_schema.users(id)';

CREATE TABLE chat_schema.room_participants (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES chat_schema.rooms(id),
    user_id UUID NULL,  -- soft link to user_schema.users(id); NULL => guest
    role_id INT NOT NULL REFERENCES chat_schema.participant_roles(id),
    guest_display_name VARCHAR(100) NULL,
    guest_gender_id INT NULL,  -- soft link to user_schema.genders(id)
    guest_age INT NULL,
    context_snapshot JSONB NULL,
    consent_start_at TIMESTAMP WITH TIME ZONE NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NULL
);

COMMENT ON COLUMN chat_schema.room_participants.user_id IS 'Soft link to user_schema.users(id)';
COMMENT ON COLUMN chat_schema.room_participants.guest_gender_id IS 'Soft link to user_schema.genders(id)';

-- Now add the deferred FK from rooms to room_participants
ALTER TABLE chat_schema.rooms
    ADD CONSTRAINT fk_rooms_current_floor_participant
    FOREIGN KEY (current_floor_participant_id)
    REFERENCES chat_schema.room_participants(id);

CREATE TABLE chat_schema.turns (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES chat_schema.rooms(id),
    seq INT NOT NULL,
    role_id INT NOT NULL REFERENCES chat_schema.turn_roles(id),
    participant_id UUID NULL REFERENCES chat_schema.room_participants(id),
    content TEXT NOT NULL,
    prompt_tokens INT NULL,
    completion_tokens INT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE chat_schema.invites (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES chat_schema.rooms(id),
    token VARCHAR(64) UNIQUE NOT NULL,
    status_id INT NOT NULL REFERENCES chat_schema.invite_statuses(id),
    created_by UUID NOT NULL,  -- soft link to user_schema.users(id)
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

COMMENT ON COLUMN chat_schema.invites.created_by IS 'Soft link to user_schema.users(id)';

CREATE TABLE chat_schema.friend_links (
    user_a UUID NOT NULL,  -- LEAST(u1, u2)
    user_b UUID NOT NULL,  -- GREATEST(u1, u2)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_a, user_b)
);

COMMENT ON COLUMN chat_schema.friend_links.user_a IS 'Soft link to user_schema.users(id) — LEAST(u1, u2)';
COMMENT ON COLUMN chat_schema.friend_links.user_b IS 'Soft link to user_schema.users(id) — GREATEST(u1, u2)';

CREATE TABLE chat_schema.chat_outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- ============================
-- Indexes (§15.4)
-- ============================

CREATE INDEX idx_rooms_owner_created
    ON chat_schema.rooms (owner_user_id, created_at DESC);

CREATE INDEX idx_rooms_status
    ON chat_schema.rooms (status_id);

CREATE INDEX idx_room_participants_room
    ON chat_schema.room_participants (room_id);

CREATE UNIQUE INDEX uq_room_participants_room_user
    ON chat_schema.room_participants (room_id, user_id)
    WHERE user_id IS NOT NULL;

CREATE UNIQUE INDEX uq_turns_room_seq
    ON chat_schema.turns (room_id, seq);

CREATE INDEX idx_chat_outbox_events_status
    ON chat_schema.chat_outbox_events (status);
