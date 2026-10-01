--liquibase formatted sql

--changeset aurora:012-cash-housekeeping-traceability
CREATE UNIQUE INDEX ux_cash_sessions_open_user
    ON cash_sessions(opened_by_user_id)
    WHERE status = 'open';

CREATE UNIQUE INDEX ux_cash_movements_payment
    ON cash_movements(payment_id)
    WHERE payment_id IS NOT NULL;

ALTER TABLE rooms
    ADD COLUMN cleaning_user_id UUID REFERENCES users(id),
    ADD COLUMN cleaning_started_at TIMESTAMPTZ,
    ADD COLUMN cleaning_completed_at TIMESTAMPTZ,
    ADD COLUMN inspector_user_id UUID REFERENCES users(id),
    ADD COLUMN inspected_at TIMESTAMPTZ;

ALTER TABLE service_requests
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN completed_at TIMESTAMPTZ;

CREATE INDEX idx_service_requests_type_booking
    ON service_requests(type, booking_id);

ALTER TABLE inventory_movements
    DROP CONSTRAINT chk_inventory_movements_reason;

ALTER TABLE inventory_movements
    ADD CONSTRAINT chk_inventory_movements_reason
    CHECK (reason IN ('purchase', 'restock', 'consumption', 'sale', 'shrinkage', 'physical_count'));

--rollback ALTER TABLE inventory_movements DROP CONSTRAINT IF EXISTS chk_inventory_movements_reason;
--rollback ALTER TABLE inventory_movements ADD CONSTRAINT chk_inventory_movements_reason CHECK (reason IN ('purchase', 'restock', 'consumption', 'sale', 'shrinkage'));
--rollback DROP INDEX IF EXISTS idx_service_requests_type_booking;
--rollback ALTER TABLE service_requests DROP COLUMN IF EXISTS completed_at;
--rollback ALTER TABLE service_requests DROP COLUMN IF EXISTS started_at;
--rollback ALTER TABLE rooms DROP COLUMN IF EXISTS inspected_at;
--rollback ALTER TABLE rooms DROP COLUMN IF EXISTS inspector_user_id;
--rollback ALTER TABLE rooms DROP COLUMN IF EXISTS cleaning_completed_at;
--rollback ALTER TABLE rooms DROP COLUMN IF EXISTS cleaning_started_at;
--rollback ALTER TABLE rooms DROP COLUMN IF EXISTS cleaning_user_id;
--rollback DROP INDEX IF EXISTS ux_cash_movements_payment;
--rollback DROP INDEX IF EXISTS ux_cash_sessions_open_user;
