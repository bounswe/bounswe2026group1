-- Seed data for local development
-- Plain text password for all users: Test@1234
-- BCrypt hash: $2a$10$7EqJtq98hPqEX7fNZaFWoOa9smHTpzOWMcmrfSMbhBgxJdqoaFiAy

INSERT INTO registered_users (name, email, password, role) VALUES
    ('admin',      'admin@test.com',        '$2a$10$7EqJtq98hPqEX7fNZaFWoOa9smHTpzOWMcmrfSMbhBgxJdqoaFiAy', 'ADMIN'),
    ('Suzan User', 'uskudarli@gmail.com',   '$2a$10$7EqJtq98hPqEX7fNZaFWoOa9smHTpzOWMcmrfSMbhBgxJdqoaFiAy', 'USER'),
    ('user',       'user@test.com',         '$2a$10$7EqJtq98hPqEX7fNZaFWoOa9smHTpzOWMcmrfSMbhBgxJdqoaFiAy', 'USER')
ON CONFLICT DO NOTHING;

-- Reports around Bogazici University, Istanbul
INSERT INTO reports (user_id, latitude, longitude, description, tag, status, agrees, disagrees, publish_date) VALUES
    (2, 41.086194, 29.044722, 'Ramp at south entrance is broken.',          'MISSING_RAMP',    'PENDING',  3, 1, NOW()),
    (2, 41.085083, 29.045528, 'Elevator in main building is out of order.', 'BROKEN_ELEVATOR', 'VERIFIED', 5, 0, NOW()),
    (3, 41.0840,   29.0545,   'Narrow passage near library.',               'NARROW_PASSAGE',  'PENDING',  1, 2, NOW())
ON CONFLICT DO NOTHING;

INSERT INTO reports (user_id, latitude, longitude, description, tag, status, agrees, disagrees, publish_date, entry_latitude, entry_longitude, exit_latitude, exit_longitude) VALUES
    (1, 41.085693, 29.044523, 'There is actually a ramp in north campus.','RAMP','VERIFIED', 1, 2, NOW(), 41.085700, 29.044550, 41.085650, 29.044500)
ON CONFLICT DO NOTHING;

INSERT INTO comments (content, author_id, report_id, created_at) VALUES
    ('I confirmed this, the ramp has been broken for weeks.', 2, 1, NOW()),
    ('Please fix this ASAP, it affects many students.',       3, 1, NOW()),
    ('The elevator was repaired according to staff.',         3, 2, NOW())
ON CONFLICT DO NOTHING;
