--liquibase formatted sql

--changeset aurora:014-room-service-inventory
ALTER TABLE inventory_movements
    DROP CONSTRAINT chk_inventory_movements_reason;

ALTER TABLE inventory_movements
    ADD CONSTRAINT chk_inventory_movements_reason
    CHECK (reason IN ('purchase', 'restock', 'consumption', 'sale', 'shrinkage', 'physical_count', 'room_service_return'));

ALTER TABLE inventory_movements
    ADD COLUMN room_service_order_id UUID REFERENCES orders(id);

-- Los movimientos automáticos de Room Service solo pueden ser el descuento
-- (out/sale) o la devolución (in/room_service_return) de un pedido, y la
-- devolución nunca puede registrarse sin pedido.
ALTER TABLE inventory_movements
    ADD CONSTRAINT chk_inventory_movements_room_service
    CHECK (
        (room_service_order_id IS NULL AND reason <> 'room_service_return')
        OR (room_service_order_id IS NOT NULL
            AND ((type = 'out' AND reason = 'sale') OR (type = 'in' AND reason = 'room_service_return')))
    );

-- Un pedido descuenta y restaura cada artículo como máximo una vez.
CREATE UNIQUE INDEX ux_inventory_movements_room_service_order_item_type
    ON inventory_movements (room_service_order_id, inventory_item_id, type)
    WHERE room_service_order_id IS NOT NULL;

ALTER TABLE orders
    ADD COLUMN inventory_deducted_at TIMESTAMPTZ,
    ADD COLUMN inventory_restored_at TIMESTAMPTZ;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_inventory_restored_after_deducted
    CHECK (inventory_restored_at IS NULL OR inventory_deducted_at IS NOT NULL);

--rollback ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_orders_inventory_restored_after_deducted;
--rollback ALTER TABLE orders DROP COLUMN IF EXISTS inventory_restored_at;
--rollback ALTER TABLE orders DROP COLUMN IF EXISTS inventory_deducted_at;
--rollback DROP INDEX IF EXISTS ux_inventory_movements_room_service_order_item_type;
--rollback ALTER TABLE inventory_movements DROP CONSTRAINT IF EXISTS chk_inventory_movements_room_service;
--rollback ALTER TABLE inventory_movements DROP COLUMN IF EXISTS room_service_order_id;
--rollback ALTER TABLE inventory_movements DROP CONSTRAINT IF EXISTS chk_inventory_movements_reason;
--rollback ALTER TABLE inventory_movements ADD CONSTRAINT chk_inventory_movements_reason CHECK (reason IN ('purchase', 'restock', 'consumption', 'sale', 'shrinkage', 'physical_count'));
