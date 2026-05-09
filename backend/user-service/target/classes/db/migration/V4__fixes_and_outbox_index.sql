CREATE INDEX idx_user_outbox_events_status
    ON user_schema.user_outbox_events (status);

INSERT INTO user_schema.user_roles (id, name) VALUES (5, 'ADMIN');

CREATE INDEX idx_friendships_requester_id
    ON user_schema.friendships (requester_id);
CREATE INDEX idx_friendships_addressee_id
    ON user_schema.friendships (addressee_id);

ALTER TABLE user_schema.profiles ADD COLUMN bio TEXT NULL;
ALTER TABLE user_schema.profiles ADD COLUMN contact_info TEXT NULL;
