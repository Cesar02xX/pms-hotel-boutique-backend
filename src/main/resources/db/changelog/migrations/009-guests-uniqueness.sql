--liquibase formatted sql

--changeset aurora:009-guests-uniqueness
CREATE UNIQUE INDEX ux_guests_email ON guests (lower(email))
    WHERE email IS NOT NULL;

CREATE UNIQUE INDEX ux_guests_document ON guests (document_type, document_number)
    WHERE document_type IS NOT NULL AND document_number IS NOT NULL;

--rollback DROP INDEX IF EXISTS ux_guests_document;
--rollback DROP INDEX IF EXISTS ux_guests_email;
