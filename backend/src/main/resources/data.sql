-- One-time backfill for rows that predate the status/points columns
UPDATE registered_users SET status = 'ACTIVE' WHERE status IS NULL;
UPDATE registered_users SET points = 0        WHERE points IS NULL;

-- Seed data for local development
-- Plain text password for all users: Test@1234
-- BCrypt hash: $2a$10$2y4KXmQAwp.0WcnN4W1Xbe57Qbmzpj0dB5mYTk.rRolW3q3i8K6i.

-- Users: ON CONFLICT works because email has a unique constraint
INSERT INTO registered_users (name, email, password, role, status, points) VALUES
    ('Ahmet Çetin',    'admin@test.com',      '$2a$10$2y4KXmQAwp.0WcnN4W1Xbe57Qbmzpj0dB5mYTk.rRolW3q3i8K6i.', 'ADMIN', 'ACTIVE', 0),
    ('Ceren Yüksel',   'uskudarli@gmail.com', '$2a$10$2y4KXmQAwp.0WcnN4W1Xbe57Qbmzpj0dB5mYTk.rRolW3q3i8K6i.', 'USER',  'ACTIVE', 0),
    ('Yılmaz Korkmaz', 'user@test.com',       '$2a$10$2y4KXmQAwp.0WcnN4W1Xbe57Qbmzpj0dB5mYTk.rRolW3q3i8K6i.', 'USER',  'ACTIVE', 0)
ON CONFLICT DO NOTHING;

-- Categories: insert with explicit IDs for self-referencing foreign keys
-- Parent categories first (no parent_id), then leaf categories
INSERT INTO report_categories (id, name, type, parent_id, affected_profiles, measurement_schema, routing_relevant, snap_type) VALUES
    -- OBSTACLE parents
    (1,  'Ramp Issues',      'OBSTACLE', NULL, NULL, NULL, false, NULL),
    (2,  'Elevator Issues',  'OBSTACLE', NULL, NULL, NULL, false, NULL),
    (3,  'Sidewalk Issues',  'OBSTACLE', NULL, NULL, NULL, false, NULL),
    -- OBSTACLE leaves (no parent)
    (4,  'Other',            'OBSTACLE', NULL, '["WHEELCHAIR","VISUAL_IMPAIRED","STROLLER","ELDERLY"]', NULL, false, NULL),
    -- OBSTACLE leaves under Ramp Issues
    (5,  'Missing Ramp',     NULL, 1, '["WHEELCHAIR","STROLLER"]', NULL, false, NULL),
    (6,  'Too Steep',        NULL, 1, '["WHEELCHAIR"]',            '{"slope_percent":{"min":0,"max":100,"accessible_max":8.33},"width_cm":{"min":0,"max":500,"accessible_min":90}}', false, NULL),
    (7,  'Too Narrow',       NULL, 1, '["WHEELCHAIR","STROLLER"]', '{"width_cm":{"min":0,"max":500,"accessible_min":90}}', false, NULL),
    (8,  'Damaged Surface',  NULL, 1, '["WHEELCHAIR","VISUAL_IMPAIRED"]', NULL, false, NULL),
    -- OBSTACLE leaves under Elevator Issues
    (9,  'Out of Service',   NULL, 2, '["WHEELCHAIR"]', NULL, false, NULL),
    (10, 'Missing',          NULL, 2, '["WHEELCHAIR"]', NULL, false, NULL),
    -- OBSTACLE leaves under Sidewalk Issues
    (11, 'Blocked',          NULL, 3, '["WHEELCHAIR","VISUAL_IMPAIRED","STROLLER","ELDERLY"]', NULL, false, NULL),
    (12, 'Uneven Surface',   NULL, 3, '["WHEELCHAIR","VISUAL_IMPAIRED"]', NULL, false, NULL),
    (13, 'No Curb Cut',      NULL, 3, '["WHEELCHAIR","STROLLER"]', NULL, false, NULL),
    -- FEATURE leaves (routing_relevant=true only for categories with entry/exit path semantics)
    (14, 'Ramp',             'FEATURE', NULL, '["WHEELCHAIR","STROLLER"]',   '{"slope_percent":{"min":0,"max":100,"accessible_max":8.33},"width_cm":{"min":0,"max":500,"accessible_min":90},"height_cm":{"min":0,"max":300}}', true,  'STAIR'),
    (15, 'Elevator',         'FEATURE', NULL, '["WHEELCHAIR"]',              '{"width_cm":{"min":0,"max":500,"accessible_min":90}}',                                                                                             false, NULL),
    (16, 'Accessible Toilet','FEATURE', NULL, '["WHEELCHAIR"]',              NULL,                                                                                                                                                false, NULL),
    (17, 'Tactile Paving',   'FEATURE', NULL, '["VISUAL_IMPAIRED"]',         NULL,                                                                                                                                                false, NULL),
    (18, 'Audio Traffic Light','FEATURE',NULL,'["VISUAL_IMPAIRED"]',         NULL,                                                                                                                                                false, NULL),
    (19, 'Accessible Parking','FEATURE',NULL, '["WHEELCHAIR"]',              NULL,                                                                                                                                                false, NULL),
    (20, 'Other',            'FEATURE', NULL, '["WHEELCHAIR","VISUAL_IMPAIRED","STROLLER","ELDERLY"]', NULL,                                                                                                                       false, NULL)
ON CONFLICT (id) DO NOTHING;

-- Advance sequence past the explicitly inserted IDs
SELECT setval('report_categories_id_seq', (SELECT MAX(id) FROM report_categories));

-- Reports: guard with NOT EXISTS since reports table has no unique constraint
INSERT INTO reports (user_id, latitude, longitude, description, category_id, environment, status, agrees, disagrees, publish_date)
SELECT 2, 41.086110, 29.044383, 'This ramp is too steep for wheelchairs', 6, 'OUTDOOR', 'PENDING', 4, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM reports WHERE description = 'This ramp is too steep for wheelchairs');

INSERT INTO reports (user_id, latitude, longitude, description, category_id, environment, status, agrees, disagrees, publish_date)
SELECT 2, 41.085243, 29.045658, 'Elevator of Boğaziçi Metro is broken', 9, 'INDOOR', 'VERIFIED', 5, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM reports WHERE description = 'Elevator of Boğaziçi Metro is broken');

INSERT INTO reports (user_id, latitude, longitude, description, category_id, environment, status, agrees, disagrees, publish_date)
SELECT 3, 41.087167, 29.043898, 'Narrow passage on the route to dormitories', 7, 'OUTDOOR', 'PENDING', 1, 2, NOW()
WHERE NOT EXISTS (SELECT 1 FROM reports WHERE description = 'Narrow passage on the route to dormitories');

INSERT INTO reports (user_id, latitude, longitude, description, category_id, environment, status, agrees, disagrees, publish_date, entry_latitude, entry_longitude, exit_latitude, exit_longitude)
SELECT 1, 41.085693, 29.044523, 'There is actually a ramp in north campus.', 14, 'OUTDOOR', 'VERIFIED', 4, 0, NOW(), 41.085700, 29.044550, 41.085650, 29.044500
WHERE NOT EXISTS (SELECT 1 FROM reports WHERE description = 'There is actually a ramp in north campus.');

-- Comments: resolve report_id via the parent report's description so this stays
-- FK-safe even if reports get deleted/re-inserted with new auto-generated ids.
-- Each insert is a no-op if the matching comment already exists, or if the
-- referenced parent report cannot be found.
INSERT INTO comments (content, author_id, report_id, created_at)
SELECT 'Yes, people also fall from this ramp', 2, r.report_id, NOW()
FROM reports r
WHERE r.description = 'This ramp is too steep for wheelchairs'
  AND NOT EXISTS (
    SELECT 1 FROM comments c
    WHERE c.content = 'Yes, people also fall from this ramp' AND c.report_id = r.report_id
  );

INSERT INTO comments (content, author_id, report_id, created_at)
SELECT 'Please fix this ASAP, it affects many students.', 3, r.report_id, NOW()
FROM reports r
WHERE r.description = 'This ramp is too steep for wheelchairs'
  AND NOT EXISTS (
    SELECT 1 FROM comments c
    WHERE c.content = 'Please fix this ASAP, it affects many students.' AND c.report_id = r.report_id
  );

INSERT INTO comments (content, author_id, report_id, created_at)
SELECT 'The elevator was repaired according to staff.', 3, r.report_id, NOW()
FROM reports r
WHERE r.description = 'Elevator of Boğaziçi Metro is broken'
  AND NOT EXISTS (
    SELECT 1 FROM comments c
    WHERE c.content = 'The elevator was repaired according to staff.' AND c.report_id = r.report_id
  );
