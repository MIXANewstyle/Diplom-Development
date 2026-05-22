-- Composite indexes for sort modes (spec §11.2.4)
CREATE INDEX idx_posts_most_liked
    ON content_schema.posts (status_id, upvotes_count DESC, published_at DESC, id DESC);

CREATE INDEX idx_posts_most_commented
    ON content_schema.posts (status_id, comments_count DESC, published_at DESC, id DESC);

CREATE INDEX idx_posts_newest
    ON content_schema.posts (status_id, published_at DESC, id DESC);

-- Author page index (used by Phase 6.3, ready now)
CREATE INDEX idx_posts_author_published
    ON content_schema.posts (author_id, published_at DESC);

-- Full-text search vector (used by Phase 6.3)
-- Combines title + content::text via 'russian' config.
-- Note: simple/russian/english — for MVP one config; spec mentions
-- multilingual, can be enhanced post-MVP.
ALTER TABLE content_schema.posts ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector('russian',
            coalesce(title, '') || ' ' || coalesce(content::text, ''))
    ) STORED;

CREATE INDEX idx_posts_search_vector
    ON content_schema.posts USING GIN (search_vector);

-- Keywords GIN index (used by Phase 6.3 search)
CREATE INDEX idx_posts_keywords_gin
    ON content_schema.posts USING GIN (keywords);
