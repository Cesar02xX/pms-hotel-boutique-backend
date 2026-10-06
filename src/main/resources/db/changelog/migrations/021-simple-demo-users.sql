--liquibase formatted sql

--changeset aurora:021-simple-demo-users
WITH simple_users(email, first_name, last_name, role_code, password_hash) AS (
    VALUES
        ('admin@aurora.test', 'Admin', 'Demo', 'admin', '$2a$10$iogSMv7Q4i4fy0P/G4ogx.x5gLS76q2rOEirsL.vqmt5TFY9sjw1a'),
        ('recepcion@aurora.test', 'Recepcion', 'Demo', 'reception', '$2a$10$8XxcD2tiA12x8TcNiPYy3ujRFe3ET1km0bIGIduNuMhIk.LgiZgFu'),
        ('limpieza@aurora.test', 'Limpieza', 'Demo', 'housekeeping', '$2a$10$oPm9vIQq0RpTWffTkZ6RReqdqfDlPPY47jPdfu5rpMnx59dOGbFvO'),
        ('conserjeria@aurora.test', 'Conserjeria', 'Demo', 'concierge', '$2a$10$XtMd4McYJq2kwHwtlEuXjuPznBdk6hylprfWkJ3jogaPm84H5owre'),
        ('roomservice@aurora.test', 'Room', 'Service', 'room_service', '$2a$10$mBQwuFl23B4ftsluRyBPv.DUoUv7mLF3ROFHEuOnIsMFrmqgEmKQ.')
)
INSERT INTO users (first_name, last_name, email, password_hash, role_id, status)
SELECT s.first_name, s.last_name, s.email, s.password_hash, r.id, 'active'
FROM simple_users s
JOIN roles r ON r.code = s.role_code
ON CONFLICT (email) DO UPDATE
SET first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    password_hash = EXCLUDED.password_hash,
    role_id = EXCLUDED.role_id,
    status = EXCLUDED.status,
    updated_at = now();

CREATE TEMP TABLE tmp_demo_user_merge ON COMMIT DROP AS
SELECT old_user.id AS old_id, simple_user.id AS new_id
FROM (
    VALUES
        ('admin.demo@aurora.test', 'admin@aurora.test'),
        ('recepcion.demo@aurora.test', 'recepcion@aurora.test'),
        ('limpieza.demo@aurora.test', 'limpieza@aurora.test'),
        ('conserjeria.demo@aurora.test', 'conserjeria@aurora.test'),
        ('roomservice.demo@aurora.test', 'roomservice@aurora.test')
) AS pairs(old_email, new_email)
JOIN users old_user ON lower(old_user.email) = lower(pairs.old_email)
JOIN users simple_user ON lower(simple_user.email) = lower(pairs.new_email)
WHERE old_user.id <> simple_user.id;

UPDATE charges c
SET created_by_user_id = m.new_id
FROM tmp_demo_user_merge m
WHERE c.created_by_user_id = m.old_id;

UPDATE payments p
SET processed_by_user_id = m.new_id
FROM tmp_demo_user_merge m
WHERE p.processed_by_user_id = m.old_id;

UPDATE inventory_movements im
SET responsible_user_id = m.new_id
FROM tmp_demo_user_merge m
WHERE im.responsible_user_id = m.old_id;

UPDATE cash_sessions cs
SET opened_by_user_id = m.new_id
FROM tmp_demo_user_merge m
WHERE cs.opened_by_user_id = m.old_id;

UPDATE cash_sessions cs
SET closed_by_user_id = m.new_id
FROM tmp_demo_user_merge m
WHERE cs.closed_by_user_id = m.old_id;

UPDATE cash_movements cm
SET responsible_user_id = m.new_id
FROM tmp_demo_user_merge m
WHERE cm.responsible_user_id = m.old_id;

UPDATE audit_logs al
SET user_id = m.new_id
FROM tmp_demo_user_merge m
WHERE al.user_id = m.old_id;

UPDATE service_requests sr
SET responsible_user_id = m.new_id
FROM tmp_demo_user_merge m
WHERE sr.responsible_user_id = m.old_id;

UPDATE service_requests sr
SET started_by_user_id = m.new_id
FROM tmp_demo_user_merge m
WHERE sr.started_by_user_id = m.old_id;

UPDATE service_requests sr
SET completed_by_user_id = m.new_id
FROM tmp_demo_user_merge m
WHERE sr.completed_by_user_id = m.old_id;

UPDATE rooms r
SET cleaning_user_id = m.new_id
FROM tmp_demo_user_merge m
WHERE r.cleaning_user_id = m.old_id;

UPDATE rooms r
SET cleaning_completed_by_user_id = m.new_id
FROM tmp_demo_user_merge m
WHERE r.cleaning_completed_by_user_id = m.old_id;

UPDATE rooms r
SET inspector_user_id = m.new_id
FROM tmp_demo_user_merge m
WHERE r.inspector_user_id = m.old_id;

DELETE FROM refresh_tokens rt
USING tmp_demo_user_merge m
WHERE rt.user_id = m.old_id;

DELETE FROM users u
USING tmp_demo_user_merge m
WHERE u.id = m.old_id;

--rollback UPDATE users SET password_hash = '$2a$10$VPdhNG1EyzSyjTZs0D7dI.YpMyIffy3KtGHUj8RRjK2xIxd2vjVqi' WHERE email IN ('admin@aurora.test', 'recepcion@aurora.test', 'limpieza@aurora.test', 'conserjeria@aurora.test', 'roomservice@aurora.test');
