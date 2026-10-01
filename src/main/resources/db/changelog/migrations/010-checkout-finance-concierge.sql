--liquibase formatted sql

--changeset aurora:010-checkout-finance-concierge
ALTER TABLE service_requests
    DROP CONSTRAINT chk_service_requests_status;

ALTER TABLE service_requests
    ADD CONSTRAINT chk_service_requests_status
    CHECK (status IN ('pending', 'accepted', 'in_progress', 'completed', 'rejected', 'cancelled'));

INSERT INTO permissions (key, name, description)
VALUES ('bookings.check-out', 'Check out bookings', 'Run booking checkout')
ON CONFLICT (key) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.key = 'bookings.check-out'
WHERE r.code IN ('admin', 'reception')
ON CONFLICT DO NOTHING;

--rollback DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE key = 'bookings.check-out');
--rollback DELETE FROM permissions WHERE key = 'bookings.check-out';
--rollback ALTER TABLE service_requests DROP CONSTRAINT IF EXISTS chk_service_requests_status;
--rollback ALTER TABLE service_requests ADD CONSTRAINT chk_service_requests_status CHECK (status IN ('pending', 'accepted', 'in_progress', 'completed', 'rejected'));
