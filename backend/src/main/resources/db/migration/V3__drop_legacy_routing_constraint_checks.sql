-- Drop legacy CHECK constraints on the RoutingConstraint enum collection tables.
-- Same root cause as V2: when ddl-auto=update first generated these tables it
-- emitted a CHECK enumerating the RoutingConstraint values that existed at the
-- time. Adding new enum values later (REQUIRE_CURB_RAMPS,
-- AVOID_INACCESSIBLE_CROSSINGS) does not extend the existing CHECK, so inserts
-- referencing the newer values fail at the DB layer with a constraint
-- violation, which surfaces as a 500 from PUT /api/users/me/routing-preferences.
--
-- The columns stay plain VARCHAR; enum validation is enforced application-side
-- via @Enumerated(EnumType.STRING) and Spring deserialization, mirroring V2.
--
-- IF EXISTS keeps this safe on fresh environments where the constraint may
-- never have been created (e.g. databases first stood up after the enum had
-- already grown beyond its original 10 values).
ALTER TABLE IF EXISTS user_routing_constraints
    DROP CONSTRAINT IF EXISTS user_routing_constraints_constraint_value_check;

ALTER TABLE IF EXISTS user_custom_routing_profile_constraints
    DROP CONSTRAINT IF EXISTS user_custom_routing_profile_constraints_constraint_value_check;
