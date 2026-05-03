-- Seed data for local development
-- Plain text password for all users: Test@1234
-- BCrypt hash: $2a$10$2y4KXmQAwp.0WcnN4W1Xbe57Qbmzpj0dB5mYTk.rRolW3q3i8K6i.

-- Users: ON CONFLICT works because email has a unique constraint
INSERT INTO registered_users (name, email, password, role) VALUES
    ('Ahmet Çetin',    'admin@test.com',      '$2a$10$2y4KXmQAwp.0WcnN4W1Xbe57Qbmzpj0dB5mYTk.rRolW3q3i8K6i.', 'ADMIN'),
    ('Ceren Yüksel',   'uskudarli@gmail.com', '$2a$10$2y4KXmQAwp.0WcnN4W1Xbe57Qbmzpj0dB5mYTk.rRolW3q3i8K6i.', 'USER'),
    ('Yılmaz Korkmaz', 'user@test.com',       '$2a$10$2y4KXmQAwp.0WcnN4W1Xbe57Qbmzpj0dB5mYTk.rRolW3q3i8K6i.', 'USER')
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────
-- Categories
-- Rules:
--   • type set on root/standalone nodes; NULL on children (inherited via parent chain)
--   • location_type set on every node
--   • measurement_schema on parent nodes (OBSTACLE) or directly on FEATURE leaf nodes;
--     children inherit via ReportCategory.getEffectiveMeasurementSchema()
--   • High Threshold overrides parent Door & Entrance Issues schema
--   • routing_relevant=true only for Ramp (Outdoor) until Elevator snap is implemented
-- ─────────────────────────────────────────────────────────────
INSERT INTO report_categories (id, name, type, parent_id, affected_profiles, measurement_schema, routing_relevant, snap_type, location_type) VALUES

    -- ── FEATURE / OUTDOOR ─────────────────────────────────────
    -- parents
    (100, 'Mobility',            'FEATURE', NULL, NULL, NULL, false, NULL, 'OUTDOOR'),
    (104, 'Accessible Entrance', 'FEATURE', NULL, NULL, '{"width_cm":{"min":0,"max":500,"accessible_min":100}}', false, NULL, 'OUTDOOR'),
    (106, 'Accessible Crossing', 'FEATURE', NULL, NULL, NULL, false, NULL, 'OUTDOOR'),
    -- standalone leaf
    (108, 'Tactile Paving',      'FEATURE', NULL, '["VISUAL_IMPAIRED"]', NULL, false, NULL, 'OUTDOOR'),
    -- leaves under Mobility
    (101, 'Ramp (Outdoor)',          NULL, 100, '["WHEELCHAIR","STROLLER"]', '{"slope_percent":{"min":0,"max":100,"accessible_max":5},"width_cm":{"min":0,"max":500,"accessible_min":100},"height_cm":{"min":0,"max":300}}', true,  'STAIR', 'OUTDOOR'),
    (102, 'Elevator (Outdoor)',      NULL, 100, '["WHEELCHAIR","STROLLER"]', '{"door_width_cm":{"min":0,"max":300,"accessible_min":90},"cabin_width_cm":{"min":0,"max":500,"accessible_min":120},"cabin_area_m2":{"min":0,"max":20,"accessible_min":1.80}}', false, NULL, 'OUTDOOR'),
    (103, 'Dropped Curb',            NULL, 100, '["WHEELCHAIR","STROLLER"]', NULL, false, NULL, 'OUTDOOR'),
    -- leaf under Accessible Entrance
    (105, 'Wide Entrance (Outdoor)', NULL, 104, '["WHEELCHAIR"]', NULL, false, NULL, 'OUTDOOR'),
    -- leaf under Accessible Crossing
    (107, 'Audio Traffic Light',     NULL, 106, '["VISUAL_IMPAIRED"]', NULL, false, NULL, 'OUTDOOR'),

    -- ── FEATURE / INDOOR ──────────────────────────────────────
    -- parents
    (200, 'Mobility',                  'FEATURE', NULL, NULL, NULL, false, NULL, 'INDOOR'),
    (203, 'Sanitation',                'FEATURE', NULL, NULL, NULL, false, NULL, 'INDOOR'),
    (206, 'Navigation & Orientation',  'FEATURE', NULL, NULL, NULL, false, NULL, 'INDOOR'),
    (210, 'Comfort & Assistance',      'FEATURE', NULL, NULL, NULL, false, NULL, 'INDOOR'),
    (214, 'Entrance',                  'FEATURE', NULL, NULL, '{"width_cm":{"min":0,"max":500,"accessible_min":100}}', false, NULL, 'INDOOR'),
    (218, 'Emergency & Safety',        'FEATURE', NULL, NULL, NULL, false, NULL, 'INDOOR'),
    -- leaves under Mobility
    (201, 'Ramp (Indoor)',             NULL, 200, '["WHEELCHAIR","STROLLER"]', '{"slope_percent":{"min":0,"max":100,"accessible_max":5},"width_cm":{"min":0,"max":500,"accessible_min":100},"height_cm":{"min":0,"max":300}}', false, NULL, 'INDOOR'),
    (202, 'Elevator (Indoor)',         NULL, 200, '["WHEELCHAIR","STROLLER"]', '{"door_width_cm":{"min":0,"max":300,"accessible_min":90},"cabin_width_cm":{"min":0,"max":500,"accessible_min":120},"cabin_area_m2":{"min":0,"max":20,"accessible_min":1.80}}', false, NULL, 'INDOOR'),
    -- leaves under Sanitation
    (204, 'Accessible Toilet',         NULL, 203, '["WHEELCHAIR"]', NULL, false, NULL, 'INDOOR'),
    (205, 'Adult Changing Facility',   NULL, 203, '["WHEELCHAIR"]', NULL, false, NULL, 'INDOOR'),
    -- leaves under Navigation & Orientation
    (207, 'Tactile Paving (Indoor)',   NULL, 206, '["VISUAL_IMPAIRED"]', NULL, false, NULL, 'INDOOR'),
    (208, 'Braille Signage',           NULL, 206, '["VISUAL_IMPAIRED"]', NULL, false, NULL, 'INDOOR'),
    (209, 'High Contrast Signage',     NULL, 206, '["VISUAL_IMPAIRED"]', NULL, false, NULL, 'INDOOR'),
    -- leaves under Comfort & Assistance
    (211, 'Hearing Loop',              NULL, 210, '["VISUAL_IMPAIRED"]', NULL, false, NULL, 'INDOOR'),
    (212, 'Accessible Reception',      NULL, 210, '["WHEELCHAIR"]', NULL, false, NULL, 'INDOOR'),
    (213, 'Priority Seating',          NULL, 210, '["ELDERLY"]', NULL, false, NULL, 'INDOOR'),
    -- leaves under Entrance
    (215, 'Automatic Door',            NULL, 214, '["WHEELCHAIR"]', NULL, false, NULL, 'INDOOR'),
    (216, 'Wide Entrance (Indoor)',    NULL, 214, '["WHEELCHAIR"]', NULL, false, NULL, 'INDOOR'),
    (217, 'Handrail',                  NULL, 214, '["WHEELCHAIR","ELDERLY"]', NULL, false, NULL, 'INDOOR'),
    -- leaves under Emergency & Safety
    (219, 'Visual Emergency Alarm',    NULL, 218, '["VISUAL_IMPAIRED"]', NULL, false, NULL, 'INDOOR'),
    (220, 'Evacuation Chair',          NULL, 218, '["WHEELCHAIR"]', NULL, false, NULL, 'INDOOR'),

    -- ── OBSTACLE / OUTDOOR ────────────────────────────────────
    -- parents (schema inherited by children)
    (300, 'Ramp Issues',      'OBSTACLE', NULL, NULL, '{"slope_percent":{"min":0,"max":100,"accessible_max":5},"width_cm":{"min":0,"max":500,"accessible_min":100},"height_cm":{"min":0,"max":300}}', false, NULL, 'OUTDOOR'),
    (305, 'Elevator Issues',  'OBSTACLE', NULL, NULL, '{"door_width_cm":{"min":0,"max":300,"accessible_min":90},"cabin_width_cm":{"min":0,"max":500,"accessible_min":120},"cabin_area_m2":{"min":0,"max":20,"accessible_min":1.80}}', false, NULL, 'OUTDOOR'),
    (308, 'Sidewalk Issues',  'OBSTACLE', NULL, NULL, '{"width_cm":{"min":0,"max":1000,"accessible_min":150},"height_clearance_cm":{"min":0,"max":500,"accessible_min":220}}', false, NULL, 'OUTDOOR'),
    (312, 'Crossing Issues',  'OBSTACLE', NULL, NULL, NULL, false, NULL, 'OUTDOOR'),
    (315, 'Stair Issues',     'OBSTACLE', NULL, NULL, '{"step_height_cm":{"min":0,"max":30,"accessible_max":15},"step_depth_cm":{"min":0,"max":100,"accessible_min":30}}', false, NULL, 'OUTDOOR'),
    (319, 'Lighting Issues',  'OBSTACLE', NULL, NULL, NULL, false, NULL, 'OUTDOOR'),
    -- standalone leaf
    (322, 'Other',            'OBSTACLE', NULL, '["WHEELCHAIR","VISUAL_IMPAIRED","STROLLER","ELDERLY"]', NULL, false, NULL, 'OUTDOOR'),
    -- leaves under Ramp Issues
    (301, 'Missing Ramp',                NULL, 300, '["WHEELCHAIR","STROLLER"]',            NULL, false, NULL, 'OUTDOOR'),
    (302, 'Too Steep',                   NULL, 300, '["WHEELCHAIR"]',                       NULL, false, NULL, 'OUTDOOR'),
    (303, 'Too Narrow',                  NULL, 300, '["WHEELCHAIR","STROLLER"]',            NULL, false, NULL, 'OUTDOOR'),
    (304, 'Damaged Surface',             NULL, 300, '["WHEELCHAIR","VISUAL_IMPAIRED"]',     NULL, false, NULL, 'OUTDOOR'),
    -- leaves under Elevator Issues
    (306, 'Out of Service',              NULL, 305, '["WHEELCHAIR","STROLLER"]',            NULL, false, NULL, 'OUTDOOR'),
    (307, 'Missing',                     NULL, 305, '["WHEELCHAIR","STROLLER"]',            NULL, false, NULL, 'OUTDOOR'),
    -- leaves under Sidewalk Issues
    (309, 'Blocked',                     NULL, 308, '["WHEELCHAIR","VISUAL_IMPAIRED","STROLLER","ELDERLY"]', NULL, false, NULL, 'OUTDOOR'),
    (310, 'Uneven Surface',              NULL, 308, '["WHEELCHAIR","VISUAL_IMPAIRED"]',     NULL, false, NULL, 'OUTDOOR'),
    (311, 'No Curb Cut',                 NULL, 308, '["WHEELCHAIR","STROLLER"]',            NULL, false, NULL, 'OUTDOOR'),
    -- leaves under Crossing Issues
    (313, 'No Pedestrian Crossing',      NULL, 312, '["WHEELCHAIR","VISUAL_IMPAIRED","STROLLER","ELDERLY"]', NULL, false, NULL, 'OUTDOOR'),
    (314, 'No Accessible Signal',        NULL, 312, '["VISUAL_IMPAIRED"]',                 NULL, false, NULL, 'OUTDOOR'),
    -- leaves under Stair Issues
    (316, 'No Handrail',                 NULL, 315, '["WHEELCHAIR","ELDERLY"]',            NULL, false, NULL, 'OUTDOOR'),
    (317, 'Damaged Steps',               NULL, 315, '["WHEELCHAIR","ELDERLY"]',            NULL, false, NULL, 'OUTDOOR'),
    (318, 'Non-standard Step Height/Depth', NULL, 315, '["WHEELCHAIR","ELDERLY"]',         NULL, false, NULL, 'OUTDOOR'),
    -- leaves under Lighting Issues
    (320, 'Insufficient Lighting',       NULL, 319, '["VISUAL_IMPAIRED","ELDERLY"]',       NULL, false, NULL, 'OUTDOOR'),
    (321, 'Glare / Blinding Light',      NULL, 319, '["VISUAL_IMPAIRED","ELDERLY"]',       NULL, false, NULL, 'OUTDOOR'),

    -- ── OBSTACLE / INDOOR ─────────────────────────────────────
    -- parents (schema inherited by children)
    (400, 'Door & Entrance Issues',  'OBSTACLE', NULL, NULL, '{"width_cm":{"min":0,"max":500,"accessible_min":100}}', false, NULL, 'INDOOR'),
    (405, 'Elevator Issues',         'OBSTACLE', NULL, NULL, '{"door_width_cm":{"min":0,"max":300,"accessible_min":90},"cabin_width_cm":{"min":0,"max":500,"accessible_min":120},"cabin_area_m2":{"min":0,"max":20,"accessible_min":1.80}}', false, NULL, 'INDOOR'),
    (408, 'Surface Issues',          'OBSTACLE', NULL, NULL, NULL, false, NULL, 'INDOOR'),
    (411, 'Stair Issues',            'OBSTACLE', NULL, NULL, '{"step_height_cm":{"min":0,"max":30,"accessible_max":15},"step_depth_cm":{"min":0,"max":100,"accessible_min":30}}', false, NULL, 'INDOOR'),
    (415, 'Lighting Issues',         'OBSTACLE', NULL, NULL, NULL, false, NULL, 'INDOOR'),
    (418, 'Emergency & Safety',      'OBSTACLE', NULL, NULL, NULL, false, NULL, 'INDOOR'),
    -- standalone leaves
    (421, 'No Accessible Route Between Floors', 'OBSTACLE', NULL, '["WHEELCHAIR"]', NULL, false, NULL, 'INDOOR'),
    (422, 'Other',                   'OBSTACLE', NULL, '["WHEELCHAIR","VISUAL_IMPAIRED","STROLLER","ELDERLY"]', NULL, false, NULL, 'INDOOR'),
    -- leaves under Door & Entrance Issues
    (401, 'Narrow Entrance',         NULL, 400, '["WHEELCHAIR"]',             NULL, false, NULL, 'INDOOR'),
    (402, 'Heavy Door',              NULL, 400, '["WHEELCHAIR","ELDERLY"]',   NULL, false, NULL, 'INDOOR'),
    -- High Threshold overrides parent schema: measures threshold height, not door width
    (403, 'High Threshold',          NULL, 400, '["WHEELCHAIR"]',             '{"height_cm":{"min":0,"max":30,"accessible_max":1.3}}', false, NULL, 'INDOOR'),
    (404, 'Round Door Handle',       NULL, 400, '["WHEELCHAIR"]',             NULL, false, NULL, 'INDOOR'),
    -- leaves under Elevator Issues (Indoor)
    (406, 'Out of Service',          NULL, 405, '["WHEELCHAIR","STROLLER"]',  NULL, false, NULL, 'INDOOR'),
    (407, 'Missing',                 NULL, 405, '["WHEELCHAIR","STROLLER"]',  NULL, false, NULL, 'INDOOR'),
    -- leaves under Surface Issues
    (409, 'Wet / Slippery',          NULL, 408, '["VISUAL_IMPAIRED","ELDERLY"]', NULL, false, NULL, 'INDOOR'),
    (410, 'Uneven Surface',          NULL, 408, '["VISUAL_IMPAIRED","ELDERLY"]', NULL, false, NULL, 'INDOOR'),
    -- leaves under Stair Issues (Indoor)
    (412, 'No Handrail',             NULL, 411, '["WHEELCHAIR","ELDERLY"]',   NULL, false, NULL, 'INDOOR'),
    (413, 'Damaged Steps',           NULL, 411, '["WHEELCHAIR","ELDERLY"]',   NULL, false, NULL, 'INDOOR'),
    (414, 'Non-standard Step Height/Depth', NULL, 411, '["WHEELCHAIR","ELDERLY"]', NULL, false, NULL, 'INDOOR'),
    -- leaves under Lighting Issues (Indoor)
    (416, 'Insufficient Lighting',   NULL, 415, '["VISUAL_IMPAIRED","ELDERLY"]', NULL, false, NULL, 'INDOOR'),
    (417, 'Glare / Blinding Light',  NULL, 415, '["VISUAL_IMPAIRED","ELDERLY"]', NULL, false, NULL, 'INDOOR'),
    -- leaves under Emergency & Safety (Indoor)
    (419, 'No Visual/Tactile Emergency Alarm', NULL, 418, '["WHEELCHAIR","VISUAL_IMPAIRED","STROLLER","ELDERLY"]', NULL, false, NULL, 'INDOOR'),
    (420, 'Blocked Emergency Exit',  NULL, 418, '["WHEELCHAIR","VISUAL_IMPAIRED","STROLLER","ELDERLY"]', NULL, false, NULL, 'INDOOR')

ON CONFLICT (id) DO NOTHING;

-- Advance sequence past the explicitly inserted IDs
SELECT setval('report_categories_id_seq', (SELECT MAX(id) FROM report_categories));

-- Reports: guard with NOT EXISTS since reports table has no unique constraint
INSERT INTO reports (user_id, latitude, longitude, description, category_id, environment, status, agrees, disagrees, publish_date)
SELECT 2, 41.086110, 29.044383, 'This ramp is too steep for wheelchairs', 302, 'OUTDOOR', 'PENDING', 4, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM reports WHERE description = 'This ramp is too steep for wheelchairs');

INSERT INTO reports (user_id, latitude, longitude, description, category_id, environment, status, agrees, disagrees, publish_date)
SELECT 2, 41.085243, 29.045658, 'Elevator of Boğaziçi Metro is broken', 406, 'INDOOR', 'VERIFIED', 5, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM reports WHERE description = 'Elevator of Boğaziçi Metro is broken');

INSERT INTO reports (user_id, latitude, longitude, description, category_id, environment, status, agrees, disagrees, publish_date)
SELECT 3, 41.087167, 29.043898, 'Narrow passage on the route to dormitories', 303, 'OUTDOOR', 'PENDING', 1, 2, NOW()
WHERE NOT EXISTS (SELECT 1 FROM reports WHERE description = 'Narrow passage on the route to dormitories');

INSERT INTO reports (user_id, latitude, longitude, description, category_id, environment, status, agrees, disagrees, publish_date, entry_latitude, entry_longitude, exit_latitude, exit_longitude)
SELECT 1, 41.085693, 29.044523, 'There is actually a ramp in north campus.', 101, 'OUTDOOR', 'VERIFIED', 4, 0, NOW(), 41.085700, 29.044550, 41.085650, 29.044500
WHERE NOT EXISTS (SELECT 1 FROM reports WHERE description = 'There is actually a ramp in north campus.');

-- Comments: resolve report_id via the parent report's description so this stays
-- FK-safe even if reports get deleted/re-inserted with new auto-generated ids.
INSERT INTO comments (content, author_id, report_id, created_at)
SELECT 'Yes, people also fall from this ramp', 2, r.id, NOW()
FROM reports r
WHERE r.description = 'This ramp is too steep for wheelchairs'
  AND NOT EXISTS (
    SELECT 1 FROM comments c
    WHERE c.content = 'Yes, people also fall from this ramp' AND c.report_id = r.id
  );

INSERT INTO comments (content, author_id, report_id, created_at)
SELECT 'Please fix this ASAP, it affects many students.', 3, r.id, NOW()
FROM reports r
WHERE r.description = 'This ramp is too steep for wheelchairs'
  AND NOT EXISTS (
    SELECT 1 FROM comments c
    WHERE c.content = 'Please fix this ASAP, it affects many students.' AND c.report_id = r.id
  );

INSERT INTO comments (content, author_id, report_id, created_at)
SELECT 'The elevator was repaired according to staff.', 3, r.id, NOW()
FROM reports r
WHERE r.description = 'Elevator of Boğaziçi Metro is broken'
  AND NOT EXISTS (
    SELECT 1 FROM comments c
    WHERE c.content = 'The elevator was repaired according to staff.' AND c.report_id = r.id
  );
