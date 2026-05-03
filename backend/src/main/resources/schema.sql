-- Migrate legacy timestamp columns to TIMESTAMPTZ so the API emits Z-suffixed
-- timestamps via Jackson's Instant serializer. See issue #221.
--
-- Runs after Hibernate (spring.jpa.defer-datasource-initialization=true) and
-- before data.sql. Idempotent: a no-op once the column is already TIMESTAMPTZ.
-- Existing values are reinterpreted as UTC, which matches the production JVM
-- timezone (containers run in UTC) so wall-clock instants are preserved.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'reports'
          AND column_name = 'publish_date'
          AND data_type = 'timestamp without time zone'
    ) THEN
        ALTER TABLE reports
            ALTER COLUMN publish_date TYPE TIMESTAMPTZ
            USING publish_date AT TIME ZONE 'UTC';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'comments'
          AND column_name = 'created_at'
          AND data_type = 'timestamp without time zone'
    ) THEN
        ALTER TABLE comments
            ALTER COLUMN created_at TYPE TIMESTAMPTZ
            USING created_at AT TIME ZONE 'UTC';
    END IF;
END $$;
