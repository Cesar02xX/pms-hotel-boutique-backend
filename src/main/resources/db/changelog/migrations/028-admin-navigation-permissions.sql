--liquibase formatted sql

--changeset aurora:028-admin-navigation-permissions
INSERT INTO permissions (key, name, description)
VALUES
    ('admin-nav.reception.summary', 'Admin navigation: reception summary', 'Show reception summary to admin users'),
    ('admin-nav.reception.calendar', 'Admin navigation: reception calendar', 'Show reception calendar to admin users'),
    ('admin-nav.reception.reservations', 'Admin navigation: reception reservations', 'Show reception reservations to admin users'),
    ('admin-nav.reception.guests', 'Admin navigation: guests', 'Show guest management to admin users'),
    ('admin-nav.reception.availability', 'Admin navigation: availability', 'Show availability to admin users'),
    ('admin-nav.reception.rooms', 'Admin navigation: reception rooms', 'Show reception rooms to admin users'),
    ('admin-nav.reception.cash', 'Admin navigation: reception cash', 'Show reception cash to admin users'),
    ('admin-nav.housekeeping.home', 'Admin navigation: housekeeping home', 'Show housekeeping home to admin users'),
    ('admin-nav.housekeeping.rooms', 'Admin navigation: housekeeping rooms', 'Show housekeeping rooms to admin users'),
    ('admin-nav.housekeeping.requests', 'Admin navigation: housekeeping requests', 'Show housekeeping requests to admin users'),
    ('admin-nav.housekeeping.history', 'Admin navigation: housekeeping history', 'Show housekeeping history to admin users'),
    ('admin-nav.room-service.orders', 'Admin navigation: room service orders', 'Show room service orders to admin users'),
    ('admin-nav.room-service.menu', 'Admin navigation: room service menu', 'Show room service menu to admin users'),
    ('admin-nav.room-service.history', 'Admin navigation: room service history', 'Show room service history to admin users'),
    ('admin-nav.room-service.inventory', 'Admin navigation: room service inventory', 'Show room service inventory to admin users'),
    ('admin-nav.concierge.requests', 'Admin navigation: concierge requests', 'Show concierge requests to admin users'),
    ('admin-nav.concierge.by-room', 'Admin navigation: concierge by room', 'Show concierge by room to admin users'),
    ('admin-nav.concierge.history', 'Admin navigation: concierge history', 'Show concierge history to admin users'),
    ('admin-nav.guest.home', 'Admin navigation: guest home', 'Show guest portal home to admin users'),
    ('admin-nav.guest.reservations', 'Admin navigation: guest reservations', 'Show guest reservations to admin users'),
    ('admin-nav.guest.stay', 'Admin navigation: guest stay', 'Show guest stay to admin users'),
    ('admin-nav.guest.amenities', 'Admin navigation: guest amenities', 'Show guest amenities to admin users'),
    ('admin-nav.guest.concierge', 'Admin navigation: guest concierge', 'Show guest concierge to admin users'),
    ('admin-nav.guest.services', 'Admin navigation: guest services', 'Show guest room service requests to admin users'),
    ('admin-nav.guest.room-service', 'Admin navigation: guest room service', 'Show guest room service to admin users'),
    ('admin-nav.guest.requests', 'Admin navigation: guest requests', 'Show guest requests and orders to admin users'),
    ('admin-nav.guest.notifications', 'Admin navigation: guest notifications', 'Show guest notifications to admin users')
ON CONFLICT (key) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'admin'
  AND p.key LIKE 'admin-nav.%'
ON CONFLICT DO NOTHING;

--rollback DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE key LIKE 'admin-nav.%');
--rollback DELETE FROM permissions WHERE key LIKE 'admin-nav.%';
