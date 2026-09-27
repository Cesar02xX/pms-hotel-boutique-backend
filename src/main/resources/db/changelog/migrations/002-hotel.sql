--liquibase formatted sql

--changeset aurora:002-hotel
CREATE TABLE room_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR NOT NULL UNIQUE,
    name VARCHAR NOT NULL,
    description TEXT,
    capacity INTEGER NOT NULL,
    bed_configuration VARCHAR,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_room_types_capacity CHECK (capacity > 0)
);

CREATE TABLE room_features (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE room_type_features (
    room_type_id UUID NOT NULL REFERENCES room_types(id),
    room_feature_id UUID NOT NULL REFERENCES room_features(id),
    PRIMARY KEY (room_type_id, room_feature_id)
);

CREATE TABLE rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_number VARCHAR NOT NULL UNIQUE,
    room_type_id UUID NOT NULL REFERENCES room_types(id),
    floor INTEGER,
    status VARCHAR NOT NULL,
    housekeeping_status VARCHAR NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_rooms_status CHECK (status IN ('available', 'occupied', 'maintenance', 'out_of_service')),
    CONSTRAINT chk_rooms_housekeeping_status CHECK (housekeeping_status IN ('dirty', 'cleaning', 'clean', 'inspected'))
);

CREATE TABLE rates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_type_id UUID NOT NULL REFERENCES room_types(id),
    name VARCHAR NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    price_cents BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'GTQ',
    minimum_nights INTEGER NOT NULL,
    refundable BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_rates_valid_dates CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT chk_rates_price_cents CHECK (price_cents >= 0),
    CONSTRAINT chk_rates_currency CHECK (currency = 'GTQ'),
    CONSTRAINT chk_rates_minimum_nights CHECK (minimum_nights > 0)
);

CREATE INDEX idx_rooms_room_type_id ON rooms(room_type_id);
CREATE INDEX idx_rooms_status ON rooms(status);
CREATE INDEX idx_rates_room_type_id ON rates(room_type_id);

--rollback DROP TABLE IF EXISTS rates;
--rollback DROP TABLE IF EXISTS rooms;
--rollback DROP TABLE IF EXISTS room_type_features;
--rollback DROP TABLE IF EXISTS room_features;
--rollback DROP TABLE IF EXISTS room_types;
