-- Seed data for local development
-- Plain text password for all users: Test@1234
-- BCrypt hash: $2a$10$2y4KXmQAwp.0WcnN4W1Xbe57Qbmzpj0dB5mYTk.rRolW3q3i8K6i.

-- Users: ON CONFLICT works because email has a unique constraint
INSERT INTO registered_users (name, email, password, role) VALUES
    ('Ahmet Çetin','admin@test.com',        '$2a$10$2y4KXmQAwp.0WcnN4W1Xbe57Qbmzpj0dB5mYTk.rRolW3q3i8K6i.', 'ADMIN'),
    ('Ceren Yüksel', 'uskudarli@gmail.com',   '$2a$10$2y4KXmQAwp.0WcnN4W1Xbe57Qbmzpj0dB5mYTk.rRolW3q3i8K6i.', 'USER'),
    ('Yılmaz Korkmaz',       'user@test.com',         '$2a$10$2y4KXmQAwp.0WcnN4W1Xbe57Qbmzpj0dB5mYTk.rRolW3q3i8K6i.', 'USER')
ON CONFLICT DO NOTHING;

-- Reports: guard with NOT EXISTS since reports table has no unique constraint
INSERT INTO reports (user_id, latitude, longitude, description, tag, status, agrees, disagrees, publish_date)
SELECT 2, 41.086110, 29.044383, 'This ramp is too steep for wheelchairs', 'MISSING_RAMP', 'PENDING', 4, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM reports WHERE description = 'This ramp is too steep for wheelchairs');

INSERT INTO reports (user_id, latitude, longitude, description, tag, status, agrees, disagrees, publish_date)
SELECT 2, 41.085243, 29.045658, 'Elevator of Boğaziçi Metro is broken', 'BROKEN_ELEVATOR', 'VERIFIED', 5, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM reports WHERE description = 'Elevator of Boğaziçi Metro is broken');

INSERT INTO reports (user_id, latitude, longitude, description, tag, status, agrees, disagrees, publish_date)
SELECT 3, 41.087167, 29.043898, 'Narrow passage on the route to dormitories', 'NARROW_PASSAGE', 'PENDING', 1, 2, NOW()
WHERE NOT EXISTS (SELECT 1 FROM reports WHERE description = 'Narrow passage on the route to dormitories');

INSERT INTO reports (user_id, latitude, longitude, description, tag, status, agrees, disagrees, publish_date, entry_latitude, entry_longitude, exit_latitude, exit_longitude)
SELECT 1, 41.085693, 29.044523, 'There is actually a ramp in north campus.', 'RAMP', 'VERIFIED', 4, 0, NOW(), 41.085700, 29.044550, 41.085650, 29.044500
WHERE NOT EXISTS (SELECT 1 FROM reports WHERE description = 'There is actually a ramp in north campus.');

-- Comments: guard with NOT EXISTS on content + report_id
INSERT INTO comments (content, author_id, report_id, created_at)
SELECT 'Yes, people also fall from this ramp', 2, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM comments WHERE content = 'Yes, people also fall from this ramp' AND report_id = 1);

INSERT INTO comments (content, author_id, report_id, created_at)
SELECT 'Please fix this ASAP, it affects many students.', 3, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM comments WHERE content = 'Please fix this ASAP, it affects many students.' AND report_id = 1);

INSERT INTO comments (content, author_id, report_id, created_at)
SELECT 'The elevator was repaired according to staff.', 3, 2, NOW()
WHERE NOT EXISTS (SELECT 1 FROM comments WHERE content = 'The elevator was repaired according to staff.' AND report_id = 2);
