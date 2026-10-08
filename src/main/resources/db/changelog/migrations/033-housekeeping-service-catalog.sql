--liquibase formatted sql
--changeset aurora:033-housekeeping-service-catalog
CREATE TABLE housekeeping_services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_housekeeping_services_name UNIQUE (name)
);

INSERT INTO housekeeping_services (name, description) VALUES
    ('Limpieza de estancia', 'Limpieza de la habitación durante tu estancia.'),
    ('Limpieza de salida', 'Preparación de la habitación para la salida.'),
    ('Limpieza completa', 'Limpieza completa de la habitación.');

ALTER TABLE service_requests
    ADD COLUMN housekeeping_service_id UUID REFERENCES housekeeping_services(id);

CREATE INDEX idx_service_requests_housekeeping_service_id
    ON service_requests(housekeeping_service_id);

--rollback DROP INDEX IF EXISTS idx_service_requests_housekeeping_service_id;
--rollback ALTER TABLE service_requests DROP COLUMN IF EXISTS housekeeping_service_id;
--rollback DROP TABLE IF EXISTS housekeeping_services;
