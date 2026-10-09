--liquibase formatted sql

--changeset aurora:029-extend-demo-guest-stays
-- Keep the checked-in demo guests usable after their originally fixed seed dates pass.
UPDATE bookings
SET check_in = CURRENT_DATE - 1,
    check_out = CURRENT_DATE + 7,
    updated_at = now()
WHERE confirmation_code IN ('AUR-DEMO-001', 'AUR-DEMO-002')
  AND status = 'checked_in'
  AND (check_in > CURRENT_DATE OR check_out <= CURRENT_DATE);

--rollback UPDATE bookings SET check_in = CASE confirmation_code WHEN 'AUR-DEMO-001' THEN DATE '2026-10-04' ELSE DATE '2026-10-05' END, check_out = CASE confirmation_code WHEN 'AUR-DEMO-001' THEN DATE '2026-10-07' ELSE DATE '2026-10-08' END, updated_at = now() WHERE confirmation_code IN ('AUR-DEMO-001', 'AUR-DEMO-002') AND status = 'checked_in';
