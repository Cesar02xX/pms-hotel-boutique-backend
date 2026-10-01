--liquibase formatted sql

--changeset aurora:015-room-service-folio-charge
-- Este indice evita que un mismo cargo de folio se vincule a mas de un pedido
-- de Room Service. La idempotencia de "un cargo por pedido" se garantiza
-- principalmente mediante el bloqueo pesimista del pedido, delivered como
-- estado terminal y la comprobacion de chargeId antes de crear el cargo.
CREATE UNIQUE INDEX ux_orders_charge_id
    ON orders (charge_id)
    WHERE charge_id IS NOT NULL;

--rollback DROP INDEX IF EXISTS ux_orders_charge_id;
