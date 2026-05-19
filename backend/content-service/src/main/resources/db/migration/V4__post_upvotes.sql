CREATE TABLE content_schema.post_upvotes (
    post_id UUID NOT NULL REFERENCES content_schema.posts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (post_id, user_id)
);

CREATE INDEX idx_post_upvotes_user_id ON content_schema.post_upvotes(user_id);
