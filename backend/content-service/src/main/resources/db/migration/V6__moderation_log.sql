CREATE TABLE content_schema.moderation_log (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id UUID NOT NULL,
    "before" JSONB NULL,
    "after" JSONB NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_moderation_log_actor_id   ON content_schema.moderation_log(actor_id);
CREATE INDEX idx_moderation_log_target_id  ON content_schema.moderation_log(target_id);
CREATE INDEX idx_moderation_log_created_at ON content_schema.moderation_log(created_at);
