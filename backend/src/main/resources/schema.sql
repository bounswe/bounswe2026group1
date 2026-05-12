-- Intentionally minimal. Spring's ScriptUtils can't parse PostgreSQL
-- dollar-quoted blocks (see #404), and rejects comment-only files with
-- "script must not be null or empty" -- so we keep one no-op statement.
-- Hibernate creates TIMESTAMPTZ columns directly from Instant entity
-- fields, so no real migration runs here.

-- Add report_type column if missing (idempotent migration from category-based model)
ALTER TABLE reports ADD COLUMN IF NOT EXISTS report_type VARCHAR(255);

-- New tables for multi-object report model
CREATE TABLE IF NOT EXISTS report_objects (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT REFERENCES reports(report_id) ON DELETE CASCADE,
    object_type VARCHAR(255) NOT NULL,
    measurements TEXT
);

CREATE TABLE IF NOT EXISTS report_object_issues (
    report_object_id BIGINT REFERENCES report_objects(id) ON DELETE CASCADE,
    issue_type VARCHAR(255) NOT NULL,
    PRIMARY KEY (report_object_id, issue_type)
);

-- One-time backfill for rows that predate the status/points columns
UPDATE registered_users SET status = 'ACTIVE' WHERE status IS NULL;
UPDATE registered_users SET points = 0        WHERE points IS NULL;

-- Backfill report_subscriptions for rows that predate the single-source-of-truth
-- audience refactor. NotificationService.resolveAudience now reads only from
-- report_subscriptions, so authors / voters / commenters of pre-existing
-- reports need their rows materialised or they would silently stop receiving
-- STATUS_CHANGE / NEW_COMMENT notifications. Idempotent — the NOT EXISTS guard
-- and the unique (user_id, report_id) constraint make it safe to re-run.
-- Hibernate (ddl-auto=update) creates the report_subscriptions table before
-- schema.sql runs (defer-datasource-initialization=true), so we can reference
-- it directly. Plain statements only — ScriptUtils chokes on PL/pgSQL DO blocks.
INSERT INTO report_subscriptions (user_id, report_id, created_at)
SELECT r.user_id, r.report_id, NOW()
FROM reports r
WHERE r.user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM report_subscriptions s
      WHERE s.user_id = r.user_id AND s.report_id = r.report_id
  );

INSERT INTO report_subscriptions (user_id, report_id, created_at)
SELECT DISTINCT v.user_id, v.report_id, NOW()
FROM report_verifications v
WHERE v.user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM report_subscriptions s
      WHERE s.user_id = v.user_id AND s.report_id = v.report_id
  );

INSERT INTO report_subscriptions (user_id, report_id, created_at)
SELECT DISTINCT c.author_id, c.report_id, NOW()
FROM comments c
WHERE c.author_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM report_subscriptions s
      WHERE s.user_id = c.author_id AND s.report_id = c.report_id
  );
