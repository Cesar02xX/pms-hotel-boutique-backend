--liquibase formatted sql

--changeset aurora:003-guests-bookings
CREATE TABLE guests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name VARCHAR NOT NULL,
    last_name VARCHAR NOT NULL,
    email VARCHAR,
    phone VARCHAR,
    nationality VARCHAR,
    document_type VARCHAR,
    document_number VARCHAR,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_guests_document_type CHECK (document_type IS NULL OR document_type IN ('passport', 'national_id', 'driver_license'))
);

CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    confirmation_code VARCHAR NOT NULL UNIQUE,
    guest_link_code VARCHAR NOT NULL UNIQUE,
    guest_id UUID NOT NULL REFERENCES guests(id),
    room_id UUID REFERENCES rooms(id),
    room_type_id UUID NOT NULL REFERENCES room_types(id),
    rate_id UUID REFERENCES rates(id),
    check_in DATE NOT NULL,
    check_out DATE NOT NULL,
    status VARCHAR NOT NULL,
    adults INTEGER NOT NULL,
    children INTEGER NOT NULL,
    total_amount_cents BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'GTQ',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_bookings_dates CHECK (check_out > check_in),
    CONSTRAINT chk_bookings_status CHECK (status IN ('pending', 'confirmed', 'checked_in', 'checked_out', 'cancelled', 'no_show')),
    CONSTRAINT chk_bookings_adults CHECK (adults >= 0),
    CONSTRAINT chk_bookings_children CHECK (children >= 0),
    CONSTRAINT chk_bookings_total_amount CHECK (total_amount_cents >= 0),
    CONSTRAINT chk_bookings_currency CHECK (currency = 'GTQ')
);

CREATE TABLE booking_companions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    first_name VARCHAR NOT NULL,
    last_name VARCHAR NOT NULL,
    document_type VARCHAR,
    document_number VARCHAR,
    guest_type VARCHAR NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_booking_companions_document_type CHECK (document_type IS NULL OR document_type IN ('passport', 'national_id', 'driver_license')),
    CONSTRAINT chk_booking_companions_guest_type CHECK (guest_type IN ('adult', 'child'))
);

CREATE INDEX idx_bookings_guest_id ON bookings(guest_id);
CREATE INDEX idx_bookings_room_id ON bookings(room_id);
CREATE INDEX idx_bookings_room_type_id ON bookings(room_type_id);
CREATE INDEX idx_bookings_status ON bookings(status);
CREATE INDEX idx_bookings_dates ON bookings(check_in, check_out);
CREATE INDEX idx_booking_companions_booking_id ON booking_companions(booking_id);

--rollback DROP TABLE IF EXISTS booking_companions;
--rollback DROP TABLE IF EXISTS bookings;
--rollback DROP TABLE IF EXISTS guests;
