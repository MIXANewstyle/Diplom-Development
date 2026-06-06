-- V3: Make joined_at nullable for room_participants
-- Invitees in PAIRED rooms are pre-created with joined_at = NULL
-- until they actually accept the invite and call /join.
ALTER TABLE chat_schema.room_participants
    ALTER COLUMN joined_at DROP NOT NULL;
