CREATE SCHEMA IF NOT EXISTS content_schema;

CREATE TABLE content_schema.post_statuses (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

INSERT INTO content_schema.post_statuses (id, name) VALUES
    (1, 'DRAFT'),
    (2, 'PUBLISHED'),
    (3, 'ARCHIVED'),
    (4, 'MODERATED');

CREATE TABLE content_schema.posts (
    id UUID PRIMARY KEY,
    author_id UUID NOT NULL,
    cover_image_url TEXT NULL,
    title VARCHAR(255) NOT NULL,
    content JSONB NULL,
    keywords TEXT[] NULL,
    status_id INT NOT NULL REFERENCES content_schema.post_statuses(id),
    published_at TIMESTAMP WITH TIME ZONE NULL,
    views_count INT NOT NULL DEFAULT 0,
    upvotes_count INT NOT NULL DEFAULT 0,
    comments_count INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE content_schema.content_outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_content_outbox_events_status
    ON content_schema.content_outbox_events (status);
