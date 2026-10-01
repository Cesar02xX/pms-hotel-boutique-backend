--liquibase formatted sql

--changeset aurora:008-security-permissions
INSERT INTO permissions (key, name, description)
VALUES
    ('rooms.read', 'Read rooms', 'View rooms'),
    ('rooms.write', 'Write rooms', 'Create and update rooms'),
    ('room-types.read', 'Read room types', 'View room types'),
    ('room-types.write', 'Write room types', 'Create and update room types'),
    ('room-features.read', 'Read room features', 'View room features'),
    ('rates.read', 'Read rates', 'View rates'),
    ('rates.write', 'Write rates', 'Create and update rates'),
    ('guests.read', 'Read guests', 'View guests'),
    ('guests.write', 'Write guests', 'Create and update guests'),
    ('bookings.read', 'Read bookings', 'View bookings'),
    ('bookings.write', 'Write bookings', 'Create and update bookings'),
    ('bookings.check-in', 'Check in bookings', 'Run booking check-in'),
    ('booking-companions.read', 'Read booking companions', 'View booking companions'),
    ('booking-companions.write', 'Write booking companions', 'Create, update and delete booking companions'),
    ('housekeeping.read', 'Read housekeeping', 'View housekeeping rooms'),
    ('housekeeping.write', 'Write housekeeping', 'Update housekeeping room status'),
    ('room-service.read', 'Read room service', 'View room service products and orders'),
    ('room-service.write', 'Write room service', 'Create and update room service orders'),
    ('payments.read', 'Read payments', 'View payments'),
    ('payments.write', 'Write payments', 'Register payments'),
    ('deposits.read', 'Read deposits', 'View deposits'),
    ('deposits.write', 'Write deposits', 'Register and refund deposits'),
    ('folios.read', 'Read folios', 'View guest folios'),
    ('folios.write', 'Write folios', 'Open guest folios'),
    ('charges.read', 'Read charges', 'View folio charges'),
    ('charges.write', 'Write charges', 'Create and void folio charges'),
    ('inventory.read', 'Read inventory', 'View inventory items and movements'),
    ('inventory.write', 'Write inventory', 'Register inventory movements'),
    ('cash.read', 'Read cash', 'View cash sessions and movements'),
    ('cash.write', 'Write cash', 'Open, close and update cash sessions'),
    ('concierge.read', 'Read concierge', 'View concierge requests'),
    ('concierge.write', 'Write concierge', 'Create and update concierge requests')
ON CONFLICT (key) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'admin'
  AND p.key IN (
      'rooms.read',
      'rooms.write',
      'room-types.read',
      'room-types.write',
      'room-features.read',
      'rates.read',
      'rates.write',
      'guests.read',
      'guests.write',
      'bookings.read',
      'bookings.write',
      'bookings.check-in',
      'booking-companions.read',
      'booking-companions.write',
      'housekeeping.read',
      'housekeeping.write',
      'room-service.read',
      'room-service.write',
      'payments.read',
      'payments.write',
      'deposits.read',
      'deposits.write',
      'folios.read',
      'folios.write',
      'charges.read',
      'charges.write',
      'inventory.read',
      'inventory.write',
      'cash.read',
      'cash.write',
      'concierge.read',
      'concierge.write'
  )
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.key IN (
    'rooms.read',
    'room-types.read',
    'room-features.read',
    'rates.read',
    'guests.read',
    'guests.write',
    'bookings.read',
    'bookings.write',
    'bookings.check-in',
    'booking-companions.read',
    'booking-companions.write',
    'payments.read',
    'payments.write',
    'deposits.read',
    'deposits.write',
    'folios.read',
    'folios.write',
    'charges.read',
    'charges.write',
    'cash.read',
    'cash.write',
    'concierge.read',
    'concierge.write',
    'room-service.read',
    'room-service.write'
)
WHERE r.code = 'reception'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.key IN (
    'housekeeping.read',
    'housekeeping.write',
    'rooms.read'
)
WHERE r.code = 'housekeeping'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.key IN (
    'concierge.read',
    'concierge.write',
    'bookings.read',
    'guests.read'
)
WHERE r.code = 'concierge'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.key IN (
    'room-service.read',
    'room-service.write',
    'bookings.read',
    'guests.read'
)
WHERE r.code = 'room_service'
ON CONFLICT DO NOTHING;

--rollback DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE key IN ('rooms.read', 'rooms.write', 'room-types.read', 'room-types.write', 'room-features.read', 'rates.read', 'rates.write', 'guests.read', 'guests.write', 'bookings.read', 'bookings.write', 'bookings.check-in', 'booking-companions.read', 'booking-companions.write', 'housekeeping.read', 'housekeeping.write', 'room-service.read', 'room-service.write', 'payments.read', 'payments.write', 'deposits.read', 'deposits.write', 'folios.read', 'folios.write', 'charges.read', 'charges.write', 'inventory.read', 'inventory.write', 'cash.read', 'cash.write', 'concierge.read', 'concierge.write'));
--rollback DELETE FROM permissions WHERE key IN ('rooms.read', 'rooms.write', 'room-types.read', 'room-types.write', 'room-features.read', 'rates.read', 'rates.write', 'guests.read', 'guests.write', 'bookings.read', 'bookings.write', 'bookings.check-in', 'booking-companions.read', 'booking-companions.write', 'housekeeping.read', 'housekeeping.write', 'room-service.read', 'room-service.write', 'payments.read', 'payments.write', 'deposits.read', 'deposits.write', 'folios.read', 'folios.write', 'charges.read', 'charges.write', 'inventory.read', 'inventory.write', 'cash.read', 'cash.write', 'concierge.read', 'concierge.write');
