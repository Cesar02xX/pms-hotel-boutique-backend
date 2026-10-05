--liquibase formatted sql

--changeset aurora:020-real-demo-seed
INSERT INTO roles (code, name, active)
VALUES
    ('admin', 'Administracion', true),
    ('reception', 'Recepcion', true),
    ('housekeeping', 'Limpieza', true),
    ('concierge', 'Conserjeria', true),
    ('room_service', 'Room Service', true),
    ('guest', 'Huesped', true)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    active = EXCLUDED.active,
    updated_at = now();

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'admin'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.key IN (
    'rooms.read', 'room-types.read', 'room-features.read',
    'rates.read', 'guests.read', 'guests.write',
    'bookings.read', 'bookings.write', 'bookings.check-in', 'bookings.check-out',
    'booking-companions.read', 'booking-companions.write',
    'payments.read', 'payments.write', 'deposits.read', 'deposits.write',
    'folios.read', 'folios.write', 'charges.read', 'charges.write',
    'cash.read', 'cash.write',
    'concierge.read', 'concierge.write',
    'room-service.read', 'room-service.write',
    'service-requests.read', 'service-requests.write'
)
WHERE r.code = 'reception'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.key IN (
    'rooms.read', 'housekeeping.read', 'housekeeping.write',
    'service-requests.read', 'service-requests.write'
)
WHERE r.code = 'housekeeping'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.key IN (
    'bookings.read', 'guests.read', 'concierge.read', 'concierge.write',
    'service-requests.read'
)
WHERE r.code = 'concierge'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.key IN (
    'bookings.read', 'guests.read', 'room-service.read', 'room-service.write'
)
WHERE r.code = 'room_service'
ON CONFLICT DO NOTHING;

WITH demo_users(email, first_name, last_name, role_code) AS (
    VALUES
        ('admin.demo@aurora.test', 'Admin', 'Demo', 'admin'),
        ('recepcion.demo@aurora.test', 'Recepcion', 'Demo', 'reception'),
        ('limpieza.demo@aurora.test', 'Limpieza', 'Demo', 'housekeeping'),
        ('conserjeria.demo@aurora.test', 'Conserjeria', 'Demo', 'concierge'),
        ('roomservice.demo@aurora.test', 'Room', 'Service', 'room_service')
)
INSERT INTO users (first_name, last_name, email, password_hash, role_id, status)
SELECT d.first_name,
       d.last_name,
       d.email,
       '$2a$10$VPdhNG1EyzSyjTZs0D7dI.YpMyIffy3KtGHUj8RRjK2xIxd2vjVqi',
       r.id,
       'active'
FROM demo_users d
JOIN roles r ON r.code = d.role_code
ON CONFLICT (email) DO UPDATE
SET first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    password_hash = EXCLUDED.password_hash,
    role_id = EXCLUDED.role_id,
    status = EXCLUDED.status,
    updated_at = now();

INSERT INTO room_types (code, name, description, capacity, bed_configuration, active)
VALUES
    ('STD-DEMO', 'Estandar Demo', 'Habitacion base para recorridos de recepcion y huesped.', 2, '1 queen bed', true),
    ('DLX-DEMO', 'Deluxe Demo', 'Habitacion premium para demo de folio y servicios.', 4, '2 queen beds', true)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    capacity = EXCLUDED.capacity,
    bed_configuration = EXCLUDED.bed_configuration,
    active = EXCLUDED.active,
    updated_at = now();

INSERT INTO room_features (name, description)
SELECT feature_name, feature_description
FROM (
    VALUES
        ('WiFi demo', 'Internet incluido para habitaciones demo.'),
        ('A/C demo', 'Aire acondicionado disponible para habitaciones demo.'),
        ('Balcon demo', 'Balcon para habitaciones deluxe demo.')
) AS feature(feature_name, feature_description)
WHERE NOT EXISTS (
    SELECT 1 FROM room_features rf WHERE lower(rf.name) = lower(feature.feature_name)
);

INSERT INTO room_type_features (room_type_id, room_feature_id)
SELECT rt.id, rf.id
FROM room_types rt
JOIN room_features rf ON rf.name IN ('WiFi demo', 'A/C demo')
WHERE rt.code IN ('STD-DEMO', 'DLX-DEMO')
ON CONFLICT DO NOTHING;

INSERT INTO room_type_features (room_type_id, room_feature_id)
SELECT rt.id, rf.id
FROM room_types rt
JOIN room_features rf ON rf.name = 'Balcon demo'
WHERE rt.code = 'DLX-DEMO'
ON CONFLICT DO NOTHING;

INSERT INTO rooms (room_number, room_type_id, floor, status, housekeeping_status, notes)
SELECT room_number, rt.id, floor, status, housekeeping_status, notes
FROM (
    VALUES
        ('101', 'STD-DEMO', 1, 'available', 'inspected', 'Disponible para reserva walk-in demo.'),
        ('102', 'STD-DEMO', 1, 'maintenance', 'dirty', 'Habitacion demo con mantenimiento pendiente.'),
        ('201', 'DLX-DEMO', 2, 'occupied', 'clean', 'Ocupada por huesped demo uno.'),
        ('202', 'DLX-DEMO', 2, 'occupied', 'clean', 'Ocupada por huesped demo dos.')
) AS demo(room_number, room_type_code, floor, status, housekeeping_status, notes)
JOIN room_types rt ON rt.code = demo.room_type_code
ON CONFLICT (room_number) DO UPDATE
SET room_type_id = EXCLUDED.room_type_id,
    floor = EXCLUDED.floor,
    status = EXCLUDED.status,
    housekeeping_status = EXCLUDED.housekeeping_status,
    notes = EXCLUDED.notes,
    updated_at = now();

INSERT INTO rates (room_type_id, name, valid_from, valid_to, price_cents, currency, minimum_nights, refundable, active)
SELECT rt.id, demo.name, demo.valid_from, demo.valid_to, demo.price_cents, 'GTQ', demo.minimum_nights, true, true
FROM (
    VALUES
        ('STD-DEMO', 'Tarifa demo estandar', DATE '2026-01-01', DATE '2026-12-31', 90000, 1),
        ('DLX-DEMO', 'Tarifa demo deluxe', DATE '2026-01-01', DATE '2026-12-31', 130000, 1)
) AS demo(room_type_code, name, valid_from, valid_to, price_cents, minimum_nights)
JOIN room_types rt ON rt.code = demo.room_type_code
WHERE NOT EXISTS (
    SELECT 1 FROM rates r WHERE r.room_type_id = rt.id AND r.name = demo.name
);

WITH demo_guests(first_name, last_name, email, phone, nationality, document_type, document_number, notes) AS (
    VALUES
        ('Ana', 'Morales', 'ana.demo@aurora.test', '+502 5555 1001', 'GT', 'passport', 'DEMO-PASS-001', 'Huesped demo con estancia activa uno.'),
        ('Carlos', 'Reyes', 'carlos.demo@aurora.test', '+502 5555 1002', 'GT', 'passport', 'DEMO-PASS-002', 'Huesped demo con estancia activa dos.')
)
UPDATE guests g
SET first_name = d.first_name,
    last_name = d.last_name,
    phone = d.phone,
    nationality = d.nationality,
    document_type = d.document_type,
    document_number = d.document_number,
    notes = d.notes,
    updated_at = now()
FROM demo_guests d
WHERE lower(g.email) = lower(d.email);

WITH demo_guests(first_name, last_name, email, phone, nationality, document_type, document_number, notes) AS (
    VALUES
        ('Ana', 'Morales', 'ana.demo@aurora.test', '+502 5555 1001', 'GT', 'passport', 'DEMO-PASS-001', 'Huesped demo con estancia activa uno.'),
        ('Carlos', 'Reyes', 'carlos.demo@aurora.test', '+502 5555 1002', 'GT', 'passport', 'DEMO-PASS-002', 'Huesped demo con estancia activa dos.')
)
INSERT INTO guests (first_name, last_name, email, phone, nationality, document_type, document_number, notes)
SELECT d.first_name, d.last_name, d.email, d.phone, d.nationality, d.document_type, d.document_number, d.notes
FROM demo_guests d
WHERE NOT EXISTS (
    SELECT 1 FROM guests g WHERE lower(g.email) = lower(d.email)
);

INSERT INTO bookings (
    confirmation_code, guest_link_code, guest_id, room_id, room_type_id, rate_id,
    check_in, check_out, status, adults, children, total_amount_cents, currency, notes
)
SELECT demo.confirmation_code,
       demo.guest_link_code,
       g.id,
       room.id,
       rt.id,
       rate.id,
       demo.check_in,
       demo.check_out,
       'checked_in',
       demo.adults,
       demo.children,
       demo.total_amount_cents,
       'GTQ',
       demo.notes
FROM (
    VALUES
        ('AUR-DEMO-001', 'HUESPED-DEMO-UNO', 'ana.demo@aurora.test', '201', 'DLX-DEMO', 'Tarifa demo deluxe', DATE '2026-10-04', DATE '2026-10-07', 2, 0, 390000, 'Estancia demo activa uno.'),
        ('AUR-DEMO-002', 'HUESPED-DEMO-DOS', 'carlos.demo@aurora.test', '202', 'DLX-DEMO', 'Tarifa demo deluxe', DATE '2026-10-05', DATE '2026-10-08', 2, 1, 390000, 'Estancia demo activa dos.')
) AS demo(confirmation_code, guest_link_code, guest_email, room_number, room_type_code, rate_name, check_in, check_out, adults, children, total_amount_cents, notes)
JOIN guests g ON g.email = demo.guest_email
JOIN rooms room ON room.room_number = demo.room_number
JOIN room_types rt ON rt.code = demo.room_type_code
JOIN rates rate ON rate.room_type_id = rt.id AND rate.name = demo.rate_name
ON CONFLICT (confirmation_code) DO UPDATE
SET guest_link_code = EXCLUDED.guest_link_code,
    guest_id = EXCLUDED.guest_id,
    room_id = EXCLUDED.room_id,
    room_type_id = EXCLUDED.room_type_id,
    rate_id = EXCLUDED.rate_id,
    check_in = EXCLUDED.check_in,
    check_out = EXCLUDED.check_out,
    status = EXCLUDED.status,
    adults = EXCLUDED.adults,
    children = EXCLUDED.children,
    total_amount_cents = EXCLUDED.total_amount_cents,
    currency = EXCLUDED.currency,
    notes = EXCLUDED.notes,
    updated_at = now();

INSERT INTO booking_companions (booking_id, first_name, last_name, document_type, document_number, guest_type)
SELECT b.id, 'Mateo', 'Morales', 'passport', 'DEMO-PASS-003', 'child'
FROM bookings b
WHERE b.confirmation_code = 'AUR-DEMO-002'
  AND NOT EXISTS (
      SELECT 1 FROM booking_companions bc
      WHERE bc.booking_id = b.id AND bc.document_number = 'DEMO-PASS-003'
  );

INSERT INTO guest_accounts (booking_id, guest_id, status, balance_cents, currency, opened_at)
SELECT b.id,
       b.guest_id,
       'open',
       CASE b.confirmation_code WHEN 'AUR-DEMO-001' THEN 126500 ELSE 315000 END,
       'GTQ',
       now() - interval '1 day'
FROM bookings b
WHERE b.confirmation_code IN ('AUR-DEMO-001', 'AUR-DEMO-002')
ON CONFLICT (booking_id) DO UPDATE
SET guest_id = EXCLUDED.guest_id,
    status = EXCLUDED.status,
    balance_cents = EXCLUDED.balance_cents,
    currency = EXCLUDED.currency,
    updated_at = now();

INSERT INTO products (sku, name, description, category, price_cents, currency, stock_quantity, reorder_level, active)
VALUES
    ('DEMO-WATER', 'Agua mineral demo', 'Producto demo de minibar.', 'minibar', 1200, 'GTQ', 24, 6, true),
    ('DEMO-COFFEE', 'Cafe aurora demo', 'Bebida demo de room service.', 'food_and_beverage', 1800, 'GTQ', 30, 8, true),
    ('DEMO-SANDWICH', 'Sandwich demo', 'Alimento demo de room service.', 'food_and_beverage', 3500, 'GTQ', 18, 5, true)
ON CONFLICT (sku) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    price_cents = EXCLUDED.price_cents,
    currency = EXCLUDED.currency,
    stock_quantity = EXCLUDED.stock_quantity,
    reorder_level = EXCLUDED.reorder_level,
    active = EXCLUDED.active,
    updated_at = now();

INSERT INTO inventory_items (sku, name, description, category, unit, current_quantity, minimum_quantity, product_id, active)
SELECT 'INV-' || p.sku,
       p.name || ' inventario',
       'Inventario demo vinculado a ' || p.name,
       'room_service',
       'unidad',
       p.stock_quantity,
       p.reorder_level,
       p.id,
       true
FROM products p
WHERE p.sku IN ('DEMO-WATER', 'DEMO-COFFEE', 'DEMO-SANDWICH')
ON CONFLICT (sku) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    current_quantity = EXCLUDED.current_quantity,
    minimum_quantity = EXCLUDED.minimum_quantity,
    product_id = EXCLUDED.product_id,
    active = EXCLUDED.active,
    updated_at = now();

INSERT INTO inventory_movements (inventory_item_id, type, reason, quantity, responsible_user_id, occurred_at, notes)
SELECT ii.id, 'in', 'restock', 10, u.id, now() - interval '2 days', 'Reposicion inicial demo.'
FROM inventory_items ii
JOIN users u ON u.email = 'roomservice.demo@aurora.test'
WHERE ii.sku IN ('INV-DEMO-WATER', 'INV-DEMO-COFFEE', 'INV-DEMO-SANDWICH')
  AND NOT EXISTS (
      SELECT 1 FROM inventory_movements im
      WHERE im.inventory_item_id = ii.id AND im.reason = 'restock' AND im.notes = 'Reposicion inicial demo.'
  );

INSERT INTO deposits (booking_id, guest_id, amount_cents, currency, method, status, collected_at, notes)
SELECT b.id,
       b.guest_id,
       CASE b.confirmation_code WHEN 'AUR-DEMO-001' THEN 50000 ELSE 75000 END,
       'GTQ',
       'credit_card',
       'held',
       now() - interval '1 day',
       'Deposito demo.'
FROM bookings b
WHERE b.confirmation_code IN ('AUR-DEMO-001', 'AUR-DEMO-002')
  AND NOT EXISTS (
      SELECT 1 FROM deposits d WHERE d.booking_id = b.id AND d.notes = 'Deposito demo.'
  );

INSERT INTO payments (booking_id, amount_cents, currency, method, status, transaction_reference, paid_at, processed_by_user_id)
SELECT b.id, 100000, 'GTQ', 'credit_card', 'completed', 'PAY-DEMO-001', now() - interval '12 hours', u.id
FROM bookings b
JOIN users u ON u.email = 'recepcion.demo@aurora.test'
WHERE b.confirmation_code = 'AUR-DEMO-001'
  AND NOT EXISTS (
      SELECT 1 FROM payments p WHERE p.transaction_reference = 'PAY-DEMO-001'
  );

INSERT INTO charges (booking_id, product_id, description, quantity, unit_price_cents, amount_cents, currency, category, status, charged_at, created_by_user_id)
SELECT b.id, null, 'Estancia demo primera noche', 1, 130000, 130000, 'GTQ', 'stay', 'posted', now() - interval '1 day', u.id
FROM bookings b
JOIN users u ON u.email = 'recepcion.demo@aurora.test'
WHERE b.confirmation_code = 'AUR-DEMO-001'
  AND NOT EXISTS (
      SELECT 1 FROM charges c WHERE c.booking_id = b.id AND c.description = 'Estancia demo primera noche'
  );

INSERT INTO charges (booking_id, product_id, description, quantity, unit_price_cents, amount_cents, currency, category, status, charged_at, created_by_user_id)
SELECT b.id, p.id, 'Room service demo', 1, 6500, 6500, 'GTQ', 'consumption', 'posted', now() - interval '6 hours', u.id
FROM bookings b
JOIN products p ON p.sku = 'DEMO-SANDWICH'
JOIN users u ON u.email = 'roomservice.demo@aurora.test'
WHERE b.confirmation_code = 'AUR-DEMO-001'
  AND NOT EXISTS (
      SELECT 1 FROM charges c WHERE c.booking_id = b.id AND c.description = 'Room service demo'
  );

INSERT INTO orders (booking_id, room_id, guest_id, status, notes, currency, requested_at)
SELECT b.id, b.room_id, b.guest_id, 'delivered', 'Pedido demo entregado.', 'GTQ', now() - interval '5 hours'
FROM bookings b
WHERE b.confirmation_code = 'AUR-DEMO-001'
  AND NOT EXISTS (
      SELECT 1 FROM orders o WHERE o.booking_id = b.id AND o.notes = 'Pedido demo entregado.'
  );

INSERT INTO order_items (order_id, product_id, quantity, unit_price_cents)
SELECT o.id, p.id, 1, p.price_cents
FROM orders o
JOIN bookings b ON b.id = o.booking_id
JOIN products p ON p.sku = 'DEMO-SANDWICH'
WHERE b.confirmation_code = 'AUR-DEMO-001'
  AND o.notes = 'Pedido demo entregado.'
  AND NOT EXISTS (
      SELECT 1 FROM order_items oi WHERE oi.order_id = o.id AND oi.product_id = p.id
  );

INSERT INTO service_requests (
    booking_id, room_id, guest_id, type, description, status, notes,
    responsible_user_id, requested_at, created_at, updated_at
)
SELECT b.id, b.room_id, b.guest_id, 'housekeeping', 'Limpieza de estancia demo', 'pending',
       'Solicitud visible para limpieza.', null, now() - interval '4 hours', now() - interval '4 hours', now() - interval '4 hours'
FROM bookings b
WHERE b.confirmation_code = 'AUR-DEMO-001'
  AND NOT EXISTS (
      SELECT 1 FROM service_requests sr WHERE sr.booking_id = b.id AND sr.type = 'housekeeping' AND sr.description = 'Limpieza de estancia demo'
  );

INSERT INTO service_requests (
    booking_id, room_id, guest_id, type, description, status, notes,
    responsible_user_id, requested_at, created_at, updated_at
)
SELECT b.id, b.room_id, b.guest_id, 'concierge', 'Reservar cena demo', 'accepted',
       'Solicitud visible para conserjeria.', u.id, now() - interval '3 hours', now() - interval '3 hours', now() - interval '2 hours'
FROM bookings b
JOIN users u ON u.email = 'conserjeria.demo@aurora.test'
WHERE b.confirmation_code = 'AUR-DEMO-002'
  AND NOT EXISTS (
      SELECT 1 FROM service_requests sr WHERE sr.booking_id = b.id AND sr.type = 'concierge' AND sr.description = 'Reservar cena demo'
  );

INSERT INTO service_requests (
    booking_id, room_id, guest_id, type, description, status, notes,
    requested_at, created_at, updated_at
)
SELECT null, r.id, null, 'maintenance', 'Revisar aire acondicionado demo', 'pending',
       'Solicitud operativa sin reserva asociada.', now() - interval '2 hours', now() - interval '2 hours', now() - interval '2 hours'
FROM rooms r
WHERE r.room_number = '102'
  AND NOT EXISTS (
      SELECT 1 FROM service_requests sr WHERE sr.room_id = r.id AND sr.type = 'maintenance' AND sr.description = 'Revisar aire acondicionado demo'
  );

INSERT INTO amenities (name, description, category, location, opens_at, closes_at, active)
SELECT 'Spa demo', 'Amenidad demo para catalogo publico.', 'hotel', 'Nivel 1', TIME '09:00', TIME '18:00', true
WHERE NOT EXISTS (
    SELECT 1 FROM amenities a WHERE a.name = 'Spa demo'
);

INSERT INTO promotions (code, name, description, discount_percent, valid_from, valid_to, active)
VALUES ('DEMO10', 'Promocion demo', 'Descuento demo para validacion.', 10, DATE '2026-01-01', DATE '2026-12-31', true)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    discount_percent = EXCLUDED.discount_percent,
    valid_from = EXCLUDED.valid_from,
    valid_to = EXCLUDED.valid_to,
    active = EXCLUDED.active,
    updated_at = now();

INSERT INTO cash_sessions (opened_by_user_id, opened_at, opening_balance_cents, currency, status, expected_balance_cents, notes)
SELECT u.id, now() - interval '8 hours', 100000, 'GTQ', 'open', 100000, 'Caja demo abierta.'
FROM users u
WHERE u.email = 'recepcion.demo@aurora.test'
  AND NOT EXISTS (
      SELECT 1 FROM cash_sessions cs WHERE cs.opened_by_user_id = u.id AND cs.status = 'open'
  );

INSERT INTO cash_movements (cash_session_id, type, concept, amount_cents, currency, responsible_user_id, occurred_at)
SELECT cs.id, 'income', 'Apertura demo', 100000, 'GTQ', cs.opened_by_user_id, cs.opened_at
FROM cash_sessions cs
JOIN users u ON u.id = cs.opened_by_user_id
WHERE u.email = 'recepcion.demo@aurora.test'
  AND cs.notes = 'Caja demo abierta.'
  AND NOT EXISTS (
      SELECT 1 FROM cash_movements cm WHERE cm.cash_session_id = cs.id AND cm.concept = 'Apertura demo'
  );

INSERT INTO guest_notifications (booking_id, guest_id, type, title, message, resource_type, resource_id)
SELECT b.id, b.guest_id, 'welcome', 'Bienvenido a Aurora', 'Tu estancia demo ya esta activa.', 'booking', b.id
FROM bookings b
WHERE b.confirmation_code IN ('AUR-DEMO-001', 'AUR-DEMO-002')
ON CONFLICT DO NOTHING;

INSERT INTO audit_logs (user_id, module, action, entity_type, entity_id, occurred_at, details)
SELECT u.id, 'demo_seed', 'created', 'booking', b.id, now(), '{"source":"020-real-demo-seed"}'::jsonb
FROM users u
JOIN bookings b ON b.confirmation_code = 'AUR-DEMO-001'
WHERE u.email = 'admin.demo@aurora.test'
  AND NOT EXISTS (
      SELECT 1 FROM audit_logs al WHERE al.module = 'demo_seed' AND al.entity_id = b.id
  );

--rollback DELETE FROM audit_logs WHERE module = 'demo_seed';
--rollback DELETE FROM guest_notifications WHERE type = 'welcome' AND resource_type = 'booking';
--rollback DELETE FROM cash_movements WHERE concept = 'Apertura demo';
--rollback DELETE FROM cash_sessions WHERE notes = 'Caja demo abierta.';
--rollback DELETE FROM promotions WHERE code = 'DEMO10';
--rollback DELETE FROM amenities WHERE name = 'Spa demo';
--rollback DELETE FROM service_requests WHERE description IN ('Limpieza de estancia demo', 'Reservar cena demo', 'Revisar aire acondicionado demo');
--rollback DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE notes = 'Pedido demo entregado.');
--rollback DELETE FROM orders WHERE notes = 'Pedido demo entregado.';
--rollback DELETE FROM charges WHERE description IN ('Estancia demo primera noche', 'Room service demo');
--rollback DELETE FROM payments WHERE transaction_reference = 'PAY-DEMO-001';
--rollback DELETE FROM deposits WHERE notes = 'Deposito demo.';
--rollback DELETE FROM inventory_movements WHERE notes = 'Reposicion inicial demo.';
--rollback DELETE FROM inventory_items WHERE sku IN ('INV-DEMO-WATER', 'INV-DEMO-COFFEE', 'INV-DEMO-SANDWICH');
--rollback DELETE FROM products WHERE sku IN ('DEMO-WATER', 'DEMO-COFFEE', 'DEMO-SANDWICH');
--rollback DELETE FROM guest_accounts WHERE booking_id IN (SELECT id FROM bookings WHERE confirmation_code IN ('AUR-DEMO-001', 'AUR-DEMO-002'));
--rollback DELETE FROM booking_companions WHERE document_number = 'DEMO-PASS-003';
--rollback DELETE FROM bookings WHERE confirmation_code IN ('AUR-DEMO-001', 'AUR-DEMO-002');
--rollback DELETE FROM guests WHERE email IN ('ana.demo@aurora.test', 'carlos.demo@aurora.test');
--rollback DELETE FROM rates WHERE name IN ('Tarifa demo estandar', 'Tarifa demo deluxe');
--rollback DELETE FROM rooms WHERE room_number IN ('101', '102', '201', '202');
--rollback DELETE FROM room_type_features WHERE room_type_id IN (SELECT id FROM room_types WHERE code IN ('STD-DEMO', 'DLX-DEMO'));
--rollback DELETE FROM room_features WHERE name IN ('WiFi demo', 'A/C demo', 'Balcon demo');
--rollback DELETE FROM room_types WHERE code IN ('STD-DEMO', 'DLX-DEMO');
--rollback DELETE FROM users WHERE email IN ('admin.demo@aurora.test', 'recepcion.demo@aurora.test', 'limpieza.demo@aurora.test', 'conserjeria.demo@aurora.test', 'roomservice.demo@aurora.test');
