--liquibase formatted sql

--changeset aurora:006-inventory-cash-admin
CREATE TABLE inventory_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR NOT NULL UNIQUE,
    name VARCHAR NOT NULL,
    description TEXT,
    category VARCHAR NOT NULL,
    unit VARCHAR NOT NULL,
    current_quantity INTEGER NOT NULL DEFAULT 0,
    minimum_quantity INTEGER NOT NULL DEFAULT 0,
    product_id UUID REFERENCES products(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_inventory_items_current_quantity CHECK (current_quantity >= 0),
    CONSTRAINT chk_inventory_items_minimum_quantity CHECK (minimum_quantity >= 0)
);

CREATE TABLE inventory_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_item_id UUID NOT NULL REFERENCES inventory_items(id),
    type VARCHAR NOT NULL,
    reason VARCHAR NOT NULL,
    quantity INTEGER NOT NULL,
    responsible_user_id UUID REFERENCES users(id),
    occurred_at TIMESTAMPTZ NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_inventory_movements_type CHECK (type IN ('in', 'out')),
    CONSTRAINT chk_inventory_movements_reason CHECK (reason IN ('purchase', 'restock', 'consumption', 'sale', 'shrinkage')),
    CONSTRAINT chk_inventory_movements_quantity CHECK (quantity > 0)
);

CREATE TABLE cash_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    opened_by_user_id UUID NOT NULL REFERENCES users(id),
    opened_at TIMESTAMPTZ NOT NULL,
    opening_balance_cents BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'GTQ',
    status VARCHAR NOT NULL,
    closed_by_user_id UUID REFERENCES users(id),
    closed_at TIMESTAMPTZ,
    expected_balance_cents BIGINT,
    counted_balance_cents BIGINT,
    difference_cents BIGINT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_cash_sessions_currency CHECK (currency = 'GTQ'),
    CONSTRAINT chk_cash_sessions_status CHECK (status IN ('open', 'closed'))
);

CREATE TABLE cash_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cash_session_id UUID NOT NULL REFERENCES cash_sessions(id),
    type VARCHAR NOT NULL,
    concept VARCHAR NOT NULL,
    amount_cents BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'GTQ',
    responsible_user_id UUID REFERENCES users(id),
    occurred_at TIMESTAMPTZ NOT NULL,
    payment_id UUID REFERENCES payments(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_cash_movements_type CHECK (type IN ('income', 'expense')),
    CONSTRAINT chk_cash_movements_amount CHECK (amount_cents >= 0),
    CONSTRAINT chk_cash_movements_currency CHECK (currency = 'GTQ')
);

CREATE TABLE promotions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR NOT NULL UNIQUE,
    name VARCHAR NOT NULL,
    description TEXT,
    discount_percent INTEGER NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_promotions_discount_percent CHECK (discount_percent BETWEEN 0 AND 100),
    CONSTRAINT chk_promotions_valid_dates CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE TABLE amenities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR NOT NULL,
    description TEXT,
    category VARCHAR NOT NULL,
    location VARCHAR,
    opens_at TIME,
    closes_at TIME,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_amenities_category CHECK (category IN ('room', 'hotel', 'service'))
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    module VARCHAR NOT NULL,
    action VARCHAR NOT NULL,
    entity_type VARCHAR NOT NULL,
    entity_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_inventory_movements_item_id ON inventory_movements(inventory_item_id);
CREATE INDEX idx_cash_movements_session_id ON cash_movements(cash_session_id);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);

--rollback DROP TABLE IF EXISTS audit_logs;
--rollback DROP TABLE IF EXISTS amenities;
--rollback DROP TABLE IF EXISTS promotions;
--rollback DROP TABLE IF EXISTS cash_movements;
--rollback DROP TABLE IF EXISTS cash_sessions;
--rollback DROP TABLE IF EXISTS inventory_movements;
--rollback DROP TABLE IF EXISTS inventory_items;
