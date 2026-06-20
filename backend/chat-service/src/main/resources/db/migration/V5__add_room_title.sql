-- ============================
-- V5: Add optional room title
-- ============================

ALTER TABLE chat_schema.rooms ADD COLUMN title VARCHAR(100);
