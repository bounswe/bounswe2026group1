-- Intentionally minimal. Spring's ScriptUtils can't parse PostgreSQL
-- dollar-quoted blocks (see #404), and rejects comment-only files with
-- "script must not be null or empty" -- so we keep one no-op statement.
-- Hibernate creates TIMESTAMPTZ columns directly from Instant entity
-- fields, so no real migration runs here.
SELECT 1;
