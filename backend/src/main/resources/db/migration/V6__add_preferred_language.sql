-- Adds the per-user UI language preference (#505).
-- Hibernate's ddl-auto=update creates this column on fresh dev databases, but
-- the Flyway migration is the source of truth for prod/CI databases that
-- already have the schema baselined.

ALTER TABLE registered_users
    ADD COLUMN IF NOT EXISTS preferred_language VARCHAR(8) NOT NULL DEFAULT 'EN';
