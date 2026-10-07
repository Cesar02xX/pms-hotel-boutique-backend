--changeset aurora:030-housekeeping-item-reservations
ALTER TABLE service_requests
    ADD COLUMN inventory_item_id UUID REFERENCES inventory_items(id),
    ADD COLUMN inventory_quantity INTEGER,
    ADD CONSTRAINT chk_service_requests_inventory_reservation CHECK (
        (inventory_item_id IS NULL AND inventory_quantity IS NULL)
        OR (inventory_item_id IS NOT NULL AND inventory_quantity > 0)
    );

ALTER TABLE inventory_movements
    DROP CONSTRAINT chk_inventory_movements_reason;

ALTER TABLE inventory_movements
    ADD CONSTRAINT chk_inventory_movements_reason CHECK (
        reason IN ('purchase', 'restock', 'consumption', 'sale', 'shrinkage',
                   'physical_count', 'room_service_return', 'reservation', 'reservation_release')
    );

--rollback ALTER TABLE inventory_movements DROP CONSTRAINT IF EXISTS chk_inventory_movements_reason;
--rollback ALTER TABLE inventory_movements ADD CONSTRAINT chk_inventory_movements_reason CHECK (reason IN ('purchase', 'restock', 'consumption', 'sale', 'shrinkage', 'physical_count', 'room_service_return'));
--rollback ALTER TABLE service_requests DROP CONSTRAINT IF EXISTS chk_service_requests_inventory_reservation;
--rollback ALTER TABLE service_requests DROP COLUMN IF EXISTS inventory_quantity;
--rollback ALTER TABLE service_requests DROP COLUMN IF EXISTS inventory_item_id;
