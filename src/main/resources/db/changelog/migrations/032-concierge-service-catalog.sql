--liquibase formatted sql
--changeset aurora:032-concierge-service-catalog
CREATE TABLE concierge_services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_concierge_services_name UNIQUE (name)
);

INSERT INTO concierge_services (name, description) VALUES
    ('Taxi al aeropuerto', 'Traslado entre el hotel y el aeropuerto.'),
    ('Transporte local', 'Traslados dentro de la ciudad.'),
    ('Traslado privado', 'Traslado privado a un destino elegido.'),
    ('Reserva de restaurante', 'Ayuda para encontrar y reservar un restaurante.'),
    ('Tour o actividad', 'Tours, actividades y experiencias locales.');

ALTER TABLE service_requests
    ADD COLUMN concierge_service_id UUID REFERENCES concierge_services(id);

CREATE INDEX idx_service_requests_concierge_service_id
    ON service_requests(concierge_service_id);

--rollback DROP INDEX IF EXISTS idx_service_requests_concierge_service_id;
--rollback ALTER TABLE service_requests DROP COLUMN IF EXISTS concierge_service_id;
--rollback DROP TABLE IF EXISTS concierge_services;
