-- Drop legacy CHECK constraints on enum columns. Hibernate generated these
-- from the original 5 ObjectType / 24 IssueType enums when ddl-auto=update
-- first ran against this schema; ddl-auto does NOT extend existing CHECK
-- constraints when new enum values are added later, so inserts using the
-- newer values fail at the DB layer with a constraint violation.
--
-- The columns stay plain VARCHAR; enum validation is enforced application-side
-- via @Enumerated(EnumType.STRING) and Spring deserialization, which already
-- rejects unknown values at the JSON layer with a 400 before any DB write.
--
-- IF EXISTS keeps this safe to apply on databases that may or may not
-- have these constraints — e.g. fresh environments where the constraint
-- never existed in the first place.
ALTER TABLE report_objects        DROP CONSTRAINT IF EXISTS report_objects_object_type_check;
ALTER TABLE report_object_issues  DROP CONSTRAINT IF EXISTS report_object_issues_issue_type_check;
