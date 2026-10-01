--liquibase formatted sql

--changeset aurora:013-housekeeping-responsibility-traceability
ALTER TABLE rooms
    ADD COLUMN cleaning_completed_by_user_id UUID REFERENCES users(id);

ALTER TABLE service_requests
    ADD COLUMN started_by_user_id UUID REFERENCES users(id),
    ADD COLUMN completed_by_user_id UUID REFERENCES users(id);

--rollback ALTER TABLE service_requests DROP COLUMN IF EXISTS completed_by_user_id;
--rollback ALTER TABLE service_requests DROP COLUMN IF EXISTS started_by_user_id;
--rollback ALTER TABLE rooms DROP COLUMN IF EXISTS cleaning_completed_by_user_id;
