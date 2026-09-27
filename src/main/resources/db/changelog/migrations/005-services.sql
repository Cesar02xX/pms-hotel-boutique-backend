--liquibase formatted sql

--changeset aurora:005-services
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR NOT NULL UNIQUE,
    name VARCHAR NOT NULL,
    description TEXT,
    category VARCHAR NOT NULL,
    price_cents BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'GTQ',
    stock_quantity INTEGER NOT NULL DEFAULT 0,
    reorder_level INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_products_category CHECK (category IN ('minibar', 'shop', 'food_and_beverage', 'other')),
    CONSTRAINT chk_products_price_cents CHECK (price_cents >= 0),
    CONSTRAINT chk_products_currency CHECK (currency = 'GTQ'),
    CONSTRAINT chk_products_stock_quantity CHECK (stock_quantity >= 0),
    CONSTRAINT chk_products_reorder_level CHECK (reorder_level >= 0)
);

ALTER TABLE charges
    ADD CONSTRAINT fk_charges_product_id FOREIGN KEY (product_id) REFERENCES products(id);

CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    room_id UUID REFERENCES rooms(id),
    guest_id UUID REFERENCES guests(id),
    status VARCHAR NOT NULL,
    notes TEXT,
    currency CHAR(3) NOT NULL DEFAULT 'GTQ',
    charge_id UUID REFERENCES charges(id),
    requested_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_orders_status CHECK (status IN ('pending', 'accepted', 'preparing', 'ready', 'on_the_way', 'delivered', 'rejected', 'cancelled')),
    CONSTRAINT chk_orders_currency CHECK (currency = 'GTQ')
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL,
    unit_price_cents BIGINT NOT NULL,
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_items_unit_price CHECK (unit_price_cents >= 0)
);

CREATE TABLE service_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    room_id UUID REFERENCES rooms(id),
    guest_id UUID REFERENCES guests(id),
    type VARCHAR NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR NOT NULL,
    notes TEXT,
    charge_id UUID REFERENCES charges(id),
    requested_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_service_requests_type CHECK (type IN ('housekeeping', 'concierge', 'maintenance', 'other')),
    CONSTRAINT chk_service_requests_status CHECK (status IN ('pending', 'accepted', 'in_progress', 'completed', 'rejected'))
);

CREATE INDEX idx_orders_booking_id ON orders(booking_id);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_service_requests_booking_id ON service_requests(booking_id);

--rollback DROP TABLE IF EXISTS service_requests;
--rollback DROP TABLE IF EXISTS order_items;
--rollback DROP TABLE IF EXISTS orders;
--rollback ALTER TABLE charges DROP CONSTRAINT IF EXISTS fk_charges_product_id;
--rollback DROP TABLE IF EXISTS products;
