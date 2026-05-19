CREATE TABLE content_schema.comments (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES content_schema.posts(id),
    author_id UUID NOT NULL,
    parent_id UUID NULL REFERENCES content_schema.comments(id),
    content TEXT NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE INDEX idx_comments_post_id ON content_schema.comments(post_id);

CREATE INDEX idx_comments_post_parent_created
    ON content_schema.comments(post_id, parent_id, created_at);
