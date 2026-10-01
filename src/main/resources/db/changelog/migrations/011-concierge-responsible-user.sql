--liquibase formatted sql

--changeset aurora:011-concierge-responsible-user
ALTER TABLE service_requests
    ADD COLUMN responsible_user_id UUID;

ALTER TABLE service_requests
    ADD CONSTRAINT fk_service_requests_responsible_user_id
    FOREIGN KEY (responsible_user_id) REFERENCES users(id);

--rollback ALTER TABLE service_requests DROP CONSTRAINT IF EXISTS fk_service_requests_responsible_user_id;
--rollback ALTER TABLE service_requests DROP COLUMN IF EXISTS responsible_user_id;
