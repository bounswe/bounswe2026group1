-- Seed data for local development
-- Plain text password for all users: Test@1234
-- BCrypt hash: $2a$10$2y4KXmQAwp.0WcnN4W1Xbe57Qbmzpj0dB5mYTk.rRolW3q3i8K6i.

-- Users: ON CONFLICT works because email has a unique constraint
INSERT INTO registered_users (name, email, password, role, status, points) VALUES
    ('Ahmet Çetin',    'admin@test.com',      '$2a$10$2y4KXmQAwp.0WcnN4W1Xbe57Qbmzpj0dB5mYTk.rRolW3q3i8K6i.', 'ADMIN', 'ACTIVE', 0),
    ('Ceren Yüksel',   'uskudarli@gmail.com', '$2a$10$2y4KXmQAwp.0WcnN4W1Xbe57Qbmzpj0dB5mYTk.rRolW3q3i8K6i.', 'USER',  'ACTIVE', 0),
    ('Yılmaz Korkmaz', 'user@test.com',       '$2a$10$2y4KXmQAwp.0WcnN4W1Xbe57Qbmzpj0dB5mYTk.rRolW3q3i8K6i.', 'USER',  'ACTIVE', 0)
ON CONFLICT DO NOTHING;

-- Reports: guard with NOT EXISTS since reports table has no unique constraint
INSERT INTO reports (user_id, location, description, report_type, environment, status, agrees, disagrees, publish_date)
SELECT 2, ST_SetSRID(ST_MakePoint(29.044383, 41.086110), 4326), 'This ramp is too steep for wheelchairs', 'OBSTACLE', 'OUTDOOR', 'PENDING', 4, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM reports WHERE description = 'This ramp is too steep for wheelchairs');

INSERT INTO reports (user_id, location, description, report_type, environment, status, agrees, disagrees, publish_date)
SELECT 2, ST_SetSRID(ST_MakePoint(29.045658, 41.085243), 4326), 'Elevator of Boğaziçi Metro is broken', 'OBSTACLE', 'INDOOR', 'VERIFIED', 5, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM reports WHERE description = 'Elevator of Boğaziçi Metro is broken');

INSERT INTO reports (user_id, location, description, report_type, environment, status, agrees, disagrees, publish_date)
SELECT 3, ST_SetSRID(ST_MakePoint(29.043898, 41.087167), 4326), 'Narrow passage on the route to dormitories', 'OBSTACLE', 'OUTDOOR', 'PENDING', 1, 2, NOW()
WHERE NOT EXISTS (SELECT 1 FROM reports WHERE description = 'Narrow passage on the route to dormitories');

INSERT INTO reports (user_id, location, description, report_type, environment, status, agrees, disagrees, publish_date, entry_latitude, entry_longitude, exit_latitude, exit_longitude)
SELECT 1, ST_SetSRID(ST_MakePoint(29.044523, 41.085693), 4326), 'There is actually a ramp in north campus.', 'FEATURE', 'OUTDOOR', 'VERIFIED', 4, 0, NOW(), 41.085700, 29.044550, 41.085650, 29.044500
WHERE NOT EXISTS (SELECT 1 FROM reports WHERE description = 'There is actually a ramp in north campus.');

-- Report objects: resolve report_id by description so this is FK-safe on re-seed
INSERT INTO report_objects (report_id, object_type, measurements)
SELECT r.report_id, 'RAMP', '{"slope_percent": 15, "width_cm": 80}'
FROM reports r
WHERE r.description = 'This ramp is too steep for wheelchairs'
  AND NOT EXISTS (SELECT 1 FROM report_objects ro WHERE ro.report_id = r.report_id AND ro.object_type = 'RAMP');

INSERT INTO report_objects (report_id, object_type, measurements)
SELECT r.report_id, 'ELEVATOR', NULL
FROM reports r
WHERE r.description = 'Elevator of Boğaziçi Metro is broken'
  AND NOT EXISTS (SELECT 1 FROM report_objects ro WHERE ro.report_id = r.report_id AND ro.object_type = 'ELEVATOR');

INSERT INTO report_objects (report_id, object_type, measurements)
SELECT r.report_id, 'SIDEWALK', '{"width_cm": 70}'
FROM reports r
WHERE r.description = 'Narrow passage on the route to dormitories'
  AND NOT EXISTS (SELECT 1 FROM report_objects ro WHERE ro.report_id = r.report_id AND ro.object_type = 'SIDEWALK');

INSERT INTO report_objects (report_id, object_type, measurements)
SELECT r.report_id, 'RAMP', '{"slope_percent": 6, "width_cm": 120}'
FROM reports r
WHERE r.description = 'There is actually a ramp in north campus.'
  AND NOT EXISTS (SELECT 1 FROM report_objects ro WHERE ro.report_id = r.report_id AND ro.object_type = 'RAMP');

-- Report object issues: resolve report_object_id via join on report description + object_type
INSERT INTO report_object_issues (report_object_id, issue_type)
SELECT ro.id, 'TOO_STEEP'
FROM report_objects ro
JOIN reports r ON ro.report_id = r.report_id
WHERE r.description = 'This ramp is too steep for wheelchairs' AND ro.object_type = 'RAMP'
  AND NOT EXISTS (SELECT 1 FROM report_object_issues i WHERE i.report_object_id = ro.id AND i.issue_type = 'TOO_STEEP');

INSERT INTO report_object_issues (report_object_id, issue_type)
SELECT ro.id, 'TOO_NARROW'
FROM report_objects ro
JOIN reports r ON ro.report_id = r.report_id
WHERE r.description = 'This ramp is too steep for wheelchairs' AND ro.object_type = 'RAMP'
  AND NOT EXISTS (SELECT 1 FROM report_object_issues i WHERE i.report_object_id = ro.id AND i.issue_type = 'TOO_NARROW');

INSERT INTO report_object_issues (report_object_id, issue_type)
SELECT ro.id, 'OUT_OF_SERVICE'
FROM report_objects ro
JOIN reports r ON ro.report_id = r.report_id
WHERE r.description = 'Elevator of Boğaziçi Metro is broken' AND ro.object_type = 'ELEVATOR'
  AND NOT EXISTS (SELECT 1 FROM report_object_issues i WHERE i.report_object_id = ro.id AND i.issue_type = 'OUT_OF_SERVICE');

INSERT INTO report_object_issues (report_object_id, issue_type)
SELECT ro.id, 'TOO_NARROW'
FROM report_objects ro
JOIN reports r ON ro.report_id = r.report_id
WHERE r.description = 'Narrow passage on the route to dormitories' AND ro.object_type = 'SIDEWALK'
  AND NOT EXISTS (SELECT 1 FROM report_object_issues i WHERE i.report_object_id = ro.id AND i.issue_type = 'TOO_NARROW');

-- Comments: resolve report_id via the parent report's description so this stays
-- FK-safe even if reports get deleted/re-inserted with new auto-generated ids.
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
