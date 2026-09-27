--liquibase formatted sql

--changeset aurora:004-folio-payments
CREATE TABLE guest_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL UNIQUE REFERENCES bookings(id),
    guest_id UUID NOT NULL REFERENCES guests(id),
    status VARCHAR NOT NULL,
    balance_cents BIGINT NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL DEFAULT 'GTQ',
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_guest_accounts_status CHECK (status IN ('open', 'closed')),
    CONSTRAINT chk_guest_accounts_currency CHECK (currency = 'GTQ')
);

CREATE TABLE charges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    product_id UUID,
    description TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price_cents BIGINT NOT NULL,
    amount_cents BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'GTQ',
    category VARCHAR NOT NULL,
    status VARCHAR NOT NULL,
    charged_at TIMESTAMPTZ NOT NULL,
    created_by_user_id UUID REFERENCES users(id),
    void_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_charges_quantity CHECK (quantity > 0),
    CONSTRAINT chk_charges_unit_price CHECK (unit_price_cents >= 0),
    CONSTRAINT chk_charges_amount CHECK (amount_cents >= 0),
    CONSTRAINT chk_charges_currency CHECK (currency = 'GTQ'),
    CONSTRAINT chk_charges_category CHECK (category IN ('stay', 'consumption')),
    CONSTRAINT chk_charges_status CHECK (status IN ('pending', 'posted', 'voided'))
);

CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    amount_cents BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'GTQ',
    method VARCHAR NOT NULL,
    status VARCHAR NOT NULL,
    transaction_reference VARCHAR,
    paid_at TIMESTAMPTZ,
    processed_by_user_id UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_payments_amount CHECK (amount_cents > 0),
    CONSTRAINT chk_payments_currency CHECK (currency = 'GTQ'),
    CONSTRAINT chk_payments_method CHECK (method IN ('cash', 'credit_card', 'debit_card', 'bank_transfer', 'online')),
    CONSTRAINT chk_payments_status CHECK (status IN ('pending', 'completed', 'failed', 'refunded'))
);

CREATE TABLE deposits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    guest_id UUID NOT NULL REFERENCES guests(id),
    amount_cents BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'GTQ',
    method VARCHAR NOT NULL,
    status VARCHAR NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL,
    refunded_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_deposits_amount CHECK (amount_cents >= 0),
    CONSTRAINT chk_deposits_currency CHECK (currency = 'GTQ'),
    CONSTRAINT chk_deposits_method CHECK (method IN ('cash', 'credit_card', 'debit_card', 'bank_transfer')),
    CONSTRAINT chk_deposits_status CHECK (status IN ('held', 'refunded', 'applied'))
);

CREATE INDEX idx_charges_booking_id ON charges(booking_id);
CREATE INDEX idx_payments_booking_id ON payments(booking_id);
CREATE INDEX idx_deposits_booking_id ON deposits(booking_id);

--rollback DROP TABLE IF EXISTS deposits;
--rollback DROP TABLE IF EXISTS payments;
--rollback DROP TABLE IF EXISTS charges;
--rollback DROP TABLE IF EXISTS guest_accounts;
