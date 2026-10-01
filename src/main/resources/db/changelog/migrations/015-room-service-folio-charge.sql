--liquibase formatted sql

--changeset aurora:015-room-service-folio-charge
-- Un cargo de folio pertenece como máximo a un pedido de Room Service.
CREATE UNIQUE INDEX ux_orders_charge_id
    ON orders (charge_id)
    WHERE charge_id IS NOT NULL;

--rollback DROP INDEX IF EXISTS ux_orders_charge_id;
