--liquibase formatted sql

--changeset aurora:023-guest-portal-ui-permissions
INSERT INTO permissions (key, name, description)
VALUES
    ('guest-portal.home', 'Guest portal home', 'Show guest portal home navigation'),
    ('guest-portal.reservations', 'Guest portal reservations', 'Show guest reservations navigation'),
    ('guest-portal.stay', 'Guest portal stay', 'Show guest stay navigation'),
    ('guest-portal.amenities', 'Guest portal amenities', 'Show guest amenities navigation'),
    ('guest-portal.services', 'Guest portal services', 'Show guest in-room services navigation'),
    ('guest-portal.room-service', 'Guest portal room service', 'Show guest room service navigation'),
    ('guest-portal.requests', 'Guest portal requests', 'Show guest requests and orders navigation'),
    ('guest-portal.notifications', 'Guest portal notifications', 'Show guest notifications navigation')
ON CONFLICT (key) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.key IN (
    'guest-portal.home',
    'guest-portal.reservations',
    'guest-portal.stay',
    'guest-portal.amenities',
    'guest-portal.services',
    'guest-portal.room-service',
    'guest-portal.requests',
    'guest-portal.notifications'
)
WHERE r.code IN ('admin', 'guest')
ON CONFLICT DO NOTHING;

--rollback DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE key IN ('guest-portal.home', 'guest-portal.reservations', 'guest-portal.stay', 'guest-portal.amenities', 'guest-portal.services', 'guest-portal.room-service', 'guest-portal.requests', 'guest-portal.notifications'));
--rollback DELETE FROM permissions WHERE key IN ('guest-portal.home', 'guest-portal.reservations', 'guest-portal.stay', 'guest-portal.amenities', 'guest-portal.services', 'guest-portal.room-service', 'guest-portal.requests', 'guest-portal.notifications');
