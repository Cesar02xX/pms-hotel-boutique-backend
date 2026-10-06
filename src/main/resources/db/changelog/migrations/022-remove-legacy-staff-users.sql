--liquibase formatted sql

--changeset aurora:022-remove-legacy-staff-users
CREATE TEMP TABLE tmp_legacy_staff_user_merge ON COMMIT DROP AS
SELECT old_user.id AS old_id, simple_user.id AS new_id
FROM (
    VALUES
        ('reception@aurora.test', 'recepcion@aurora.test'),
        ('housekeeping@aurora.test', 'limpieza@aurora.test'),
        ('concierge@aurora.test', 'conserjeria@aurora.test')
) AS pairs(old_email, new_email)
JOIN users old_user ON lower(old_user.email) = lower(pairs.old_email)
JOIN users simple_user ON lower(simple_user.email) = lower(pairs.new_email)
WHERE old_user.id <> simple_user.id;

UPDATE charges c
SET created_by_user_id = m.new_id
FROM tmp_legacy_staff_user_merge m
WHERE c.created_by_user_id = m.old_id;

UPDATE payments p
SET processed_by_user_id = m.new_id
FROM tmp_legacy_staff_user_merge m
WHERE p.processed_by_user_id = m.old_id;

UPDATE inventory_movements im
SET responsible_user_id = m.new_id
FROM tmp_legacy_staff_user_merge m
WHERE im.responsible_user_id = m.old_id;

UPDATE cash_sessions cs
SET opened_by_user_id = m.new_id
FROM tmp_legacy_staff_user_merge m
WHERE cs.opened_by_user_id = m.old_id;

UPDATE cash_sessions cs
SET closed_by_user_id = m.new_id
FROM tmp_legacy_staff_user_merge m
WHERE cs.closed_by_user_id = m.old_id;

UPDATE cash_movements cm
SET responsible_user_id = m.new_id
FROM tmp_legacy_staff_user_merge m
WHERE cm.responsible_user_id = m.old_id;

UPDATE audit_logs al
SET user_id = m.new_id
FROM tmp_legacy_staff_user_merge m
WHERE al.user_id = m.old_id;

UPDATE service_requests sr
SET responsible_user_id = m.new_id
FROM tmp_legacy_staff_user_merge m
WHERE sr.responsible_user_id = m.old_id;

UPDATE service_requests sr
SET started_by_user_id = m.new_id
FROM tmp_legacy_staff_user_merge m
WHERE sr.started_by_user_id = m.old_id;

UPDATE service_requests sr
SET completed_by_user_id = m.new_id
FROM tmp_legacy_staff_user_merge m
WHERE sr.completed_by_user_id = m.old_id;

UPDATE rooms r
SET cleaning_user_id = m.new_id
FROM tmp_legacy_staff_user_merge m
WHERE r.cleaning_user_id = m.old_id;

UPDATE rooms r
SET cleaning_completed_by_user_id = m.new_id
FROM tmp_legacy_staff_user_merge m
WHERE r.cleaning_completed_by_user_id = m.old_id;

UPDATE rooms r
SET inspector_user_id = m.new_id
FROM tmp_legacy_staff_user_merge m
WHERE r.inspector_user_id = m.old_id;

DELETE FROM refresh_tokens rt
USING tmp_legacy_staff_user_merge m
WHERE rt.user_id = m.old_id;

DELETE FROM users u
USING tmp_legacy_staff_user_merge m
WHERE u.id = m.old_id;

--rollback INSERT INTO users (first_name, last_name, email, password_hash, role_id, status) SELECT 'Recepcion', 'Demo', 'reception@aurora.test', password_hash, role_id, status FROM users WHERE email = 'recepcion@aurora.test' ON CONFLICT (email) DO NOTHING;
