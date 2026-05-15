CREATE TABLE content_schema.tags (
    id UUID PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE content_schema.post_tags (
    post_id UUID NOT NULL REFERENCES content_schema.posts(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES content_schema.tags(id),
    PRIMARY KEY (post_id, tag_id)
);

CREATE INDEX idx_post_tags_tag_id ON content_schema.post_tags (tag_id);
