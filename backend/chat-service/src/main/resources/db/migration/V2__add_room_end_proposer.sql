-- ============================
-- V2: Add end proposer
-- ============================

ALTER TABLE chat_schema.rooms
    ADD COLUMN ending_proposed_by_participant_id UUID NULL,
    ADD CONSTRAINT fk_rooms_ending_proposed
    FOREIGN KEY (ending_proposed_by_participant_id)
    REFERENCES chat_schema.room_participants(id);
