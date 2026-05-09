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

-- Drop legacy CHECK constraints on enum columns. Hibernate generated these
-- from the original 5 ObjectType / 24 IssueType enums; ddl-auto=update does
-- NOT extend them when new enum values are added, so inserts with newer
-- values fail at the DB layer with a constraint violation. Without these
-- constraints the columns remain plain VARCHAR — application-side enum
-- parsing is the source of truth, and Hibernate does not re-create them
-- on subsequent boots.
ALTER TABLE report_objects        DROP CONSTRAINT IF EXISTS report_objects_object_type_check;
ALTER TABLE report_object_issues  DROP CONSTRAINT IF EXISTS report_object_issues_issue_type_check;

-- One-time backfill for rows that predate the status/points columns
UPDATE registered_users SET status = 'ACTIVE' WHERE status IS NULL;
UPDATE registered_users SET points = 0        WHERE points IS NULL;
