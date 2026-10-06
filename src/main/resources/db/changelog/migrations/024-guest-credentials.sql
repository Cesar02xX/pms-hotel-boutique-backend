--liquibase formatted sql

--changeset aurora:024-guest-credentials
CREATE TABLE guest_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guest_id UUID NOT NULL UNIQUE REFERENCES guests(id) ON DELETE CASCADE,
    email VARCHAR NOT NULL UNIQUE,
    password_hash VARCHAR NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_guest_credentials_guest_id ON guest_credentials(guest_id);
CREATE UNIQUE INDEX ux_guest_credentials_email ON guest_credentials(lower(email));

INSERT INTO guest_credentials (guest_id, email, password_hash, active)
SELECT g.id, g.email, '$2a$10$ziydBCEO73AWW5uCJpEU8.NJKCniQ5MHgdOdQ2BCJEdzAO/E7RcU.', true
FROM guests g
WHERE lower(g.email) = 'ana.demo@aurora.test'
ON CONFLICT (email) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    active = EXCLUDED.active,
    updated_at = now();

INSERT INTO guest_credentials (guest_id, email, password_hash, active)
SELECT g.id, g.email, '$2a$10$YaWYOINA.MUCqR4yT8/sK.ZFK3xkYa6skejtMVoWXh0YcEBEPuk5C', true
FROM guests g
WHERE lower(g.email) = 'carlos.demo@aurora.test'
ON CONFLICT (email) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    active = EXCLUDED.active,
    updated_at = now();

--rollback DROP TABLE IF EXISTS guest_credentials CASCADE;
