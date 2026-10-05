--liquibase formatted sql

--changeset aurora:019-service-requests-concierge-write-permission
DELETE FROM role_permissions
WHERE role_id IN (SELECT id FROM roles WHERE code = 'concierge')
  AND permission_id IN (SELECT id FROM permissions WHERE key = 'service-requests.write');

--rollback INSERT INTO role_permissions (role_id, permission_id)
--rollback SELECT r.id, p.id
--rollback FROM roles r
--rollback JOIN permissions p ON p.key = 'service-requests.write'
--rollback WHERE r.code = 'concierge'
--rollback ON CONFLICT DO NOTHING;
