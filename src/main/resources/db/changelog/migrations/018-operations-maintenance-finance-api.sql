--liquibase formatted sql

--changeset aurora:018-operations-maintenance-finance-api
ALTER TABLE bookings
    ADD COLUMN cancellation_reason TEXT,
    ADD COLUMN cancelled_at TIMESTAMPTZ;

ALTER TABLE service_requests
    ALTER COLUMN booking_id DROP NOT NULL;

CREATE INDEX idx_service_requests_type_room
    ON service_requests(type, room_id);

INSERT INTO permissions (key, name, description)
VALUES
    ('service-requests.read', 'Read service requests', 'View operational service requests'),
    ('service-requests.write', 'Write service requests', 'Create and update operational service requests')
ON CONFLICT (key) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.key IN ('service-requests.read', 'service-requests.write')
WHERE r.code IN ('admin', 'reception', 'housekeeping', 'concierge')
ON CONFLICT DO NOTHING;

--rollback DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE key IN ('service-requests.read', 'service-requests.write'));
--rollback DELETE FROM permissions WHERE key IN ('service-requests.read', 'service-requests.write');
--rollback DROP INDEX IF EXISTS idx_service_requests_type_room;
--rollback ALTER TABLE service_requests ALTER COLUMN booking_id SET NOT NULL;
--rollback ALTER TABLE bookings DROP COLUMN IF EXISTS cancelled_at;
--rollback ALTER TABLE bookings DROP COLUMN IF EXISTS cancellation_reason;
