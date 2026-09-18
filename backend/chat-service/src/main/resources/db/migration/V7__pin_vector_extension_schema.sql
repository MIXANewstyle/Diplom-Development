-- V6 created the pgvector extension without an explicit schema. Flyway runs with
-- search_path = chat_schema, so on every deployment so far the extension landed in chat_schema,
-- and the JDBC code addresses it as chat_schema.vector / OPERATOR(chat_schema.<=>).
-- Pin that placement explicitly so a database where the extension was pre-created elsewhere
-- (e.g. manually in public) behaves the same way.
DO $$
DECLARE
    ext_schema TEXT;
BEGIN
    SELECT n.nspname INTO ext_schema
    FROM pg_extension e
    JOIN pg_namespace n ON n.oid = e.extnamespace
    WHERE e.extname = 'vector';

    IF ext_schema IS NULL THEN
        CREATE EXTENSION vector SCHEMA chat_schema;
    ELSIF ext_schema <> 'chat_schema' THEN
        ALTER EXTENSION vector SET SCHEMA chat_schema;
    END IF;
END $$;
