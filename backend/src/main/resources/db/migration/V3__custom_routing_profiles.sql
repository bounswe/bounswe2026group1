-- Custom routing profiles (issue: per-user named bundles of RoutingConstraint
-- values). The tables themselves — `user_custom_routing_profiles` and the
-- element-collection `user_custom_routing_profile_constraints` — and the
-- `registered_users.active_custom_profile_id` column are emitted by Hibernate
-- via @Entity / @ElementCollection / @Column.
--
-- This migration adds the things Hibernate does NOT generate:
--   1. An index on `user_id` to keep the per-user listing query cheap.
--   2. A foreign key from `registered_users.active_custom_profile_id` back to
--      the profiles table — Hibernate emits the column as a plain Long (no
--      @ManyToOne) so no FK is created automatically. ON DELETE SET NULL keeps
--      the user row valid if a profile is deleted out-of-band.
--   3. A defensive cleanup of any stale active_custom_profile_id values
--      pointing at non-existent profiles before the FK is added.
--
-- Flyway runs before Hibernate ddl-auto on each boot. On a brand-new database
-- the entity tables don't exist yet when this migration runs, so every
-- statement is wrapped in an existence check. The ALTERs are re-applied on the
-- next boot of an already-migrated DB only if Flyway re-runs them, which it
-- won't — so the IF NOT EXISTS guards make this script safely re-runnable for
-- manual recovery as well.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = current_schema()
          AND table_name = 'user_custom_routing_profiles'
    ) THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_user_custom_routing_profiles_user_id '
                || 'ON user_custom_routing_profiles (user_id)';
    END IF;
END
$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'registered_users'
          AND column_name = 'active_custom_profile_id'
    )
    AND EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = current_schema()
          AND table_name = 'user_custom_routing_profiles'
    )
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = current_schema()
          AND table_name = 'registered_users'
          AND constraint_name = 'fk_registered_users_active_custom_profile'
    ) THEN
        -- Null out any orphan references first so the FK creation succeeds
        -- on databases where rows were deleted before this constraint existed.
        EXECUTE 'UPDATE registered_users u '
                || 'SET active_custom_profile_id = NULL '
                || 'WHERE active_custom_profile_id IS NOT NULL '
                || '  AND NOT EXISTS ('
                || '    SELECT 1 FROM user_custom_routing_profiles p '
                || '    WHERE p.id = u.active_custom_profile_id'
                || '  )';

        EXECUTE 'ALTER TABLE registered_users '
                || 'ADD CONSTRAINT fk_registered_users_active_custom_profile '
                || 'FOREIGN KEY (active_custom_profile_id) '
                || 'REFERENCES user_custom_routing_profiles (id) '
                || 'ON DELETE SET NULL';
    END IF;
END
$$;
