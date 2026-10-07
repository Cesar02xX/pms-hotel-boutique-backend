--changeset aurora:031-backfill-housekeeping-item-reservations
CREATE TEMPORARY TABLE legacy_housekeeping_item_reservations ON COMMIT DROP AS
SELECT
    sr.id AS service_request_id,
    ii.id AS inventory_item_id,
    split_part(split_part(sr.description, ': ', 2), ' × ', 1)::INTEGER AS quantity
FROM service_requests sr
JOIN inventory_items ii
  ON ii.category = 'housekeeping'
 AND split_part(split_part(sr.description, ' × ', 2), ' · ', 1) = ii.name
WHERE sr.type = 'housekeeping'
  AND sr.status IN ('pending', 'accepted')
  AND sr.inventory_item_id IS NULL
  AND sr.description LIKE 'Art%solicitados: %'
  AND split_part(split_part(sr.description, ': ', 2), ' × ', 1) ~ '^[0-9]+$';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM legacy_housekeeping_item_reservations r
        JOIN inventory_items ii ON ii.id = r.inventory_item_id
        GROUP BY ii.id, ii.name, ii.current_quantity
        HAVING SUM(r.quantity) > ii.current_quantity
    ) THEN
        RAISE EXCEPTION 'Cannot reserve existing housekeeping requests: restock items before applying migration 031';
    END IF;
END $$;

UPDATE service_requests sr
SET inventory_item_id = r.inventory_item_id,
    inventory_quantity = r.quantity
FROM legacy_housekeeping_item_reservations r
WHERE sr.id = r.service_request_id;

UPDATE inventory_items ii
SET current_quantity = ii.current_quantity - reserved.total_quantity,
    updated_at = now()
FROM (
    SELECT inventory_item_id, SUM(quantity)::INTEGER AS total_quantity
    FROM legacy_housekeeping_item_reservations
    GROUP BY inventory_item_id
) reserved
WHERE ii.id = reserved.inventory_item_id;

INSERT INTO inventory_movements (
    inventory_item_id, type, reason, quantity, responsible_user_id, occurred_at, notes, created_at
)
SELECT
    r.inventory_item_id,
    'out',
    'reservation',
    r.quantity,
    NULL,
    now(),
    'Reserva migrada 031 de solicitud: ' || r.service_request_id,
    now()
FROM legacy_housekeeping_item_reservations r;

--rollback UPDATE inventory_items ii SET current_quantity = ii.current_quantity + reservations.total_quantity FROM (SELECT inventory_item_id, SUM(quantity)::INTEGER AS total_quantity FROM inventory_movements WHERE reason = 'reservation' AND notes LIKE 'Reserva migrada 031 de solicitud: %' GROUP BY inventory_item_id) reservations WHERE ii.id = reservations.inventory_item_id;
--rollback UPDATE service_requests SET inventory_item_id = NULL, inventory_quantity = NULL WHERE id IN (SELECT right(notes, 36)::UUID FROM inventory_movements WHERE reason = 'reservation' AND notes LIKE 'Reserva migrada 031 de solicitud: %');
--rollback DELETE FROM inventory_movements WHERE reason = 'reservation' AND notes LIKE 'Reserva migrada 031 de solicitud: %';
