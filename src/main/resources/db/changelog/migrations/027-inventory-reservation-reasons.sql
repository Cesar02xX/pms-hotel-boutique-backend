--liquibase formatted sql

--changeset aurora:027-inventory-reservation-reasons
ALTER TABLE inventory_movements
    DROP CONSTRAINT chk_inventory_movements_reason;

ALTER TABLE inventory_movements
    ADD CONSTRAINT chk_inventory_movements_reason
    CHECK (reason IN (
        'purchase', 'restock', 'consumption', 'sale', 'shrinkage',
        'physical_count', 'room_service_return', 'reservation', 'reservation_release'
    ));

--rollback ALTER TABLE inventory_movements DROP CONSTRAINT IF EXISTS chk_inventory_movements_reason;
--rollback ALTER TABLE inventory_movements ADD CONSTRAINT chk_inventory_movements_reason CHECK (reason IN ('purchase', 'restock', 'consumption', 'sale', 'shrinkage', 'physical_count', 'room_service_return'));
