-- Add image_urls array column to posts (mirrors keywords pattern)
ALTER TABLE content_schema.posts
    ADD COLUMN image_urls TEXT[] NULL;

-- Backfill: copy existing cover_image_url into image_urls as the first (and only) element
UPDATE content_schema.posts
   SET image_urls = ARRAY[cover_image_url]
 WHERE cover_image_url IS NOT NULL
   AND cover_image_url <> '';
