--liquibase formatted sql

--changeset aurora:026-housekeeping-turnover-checklists
ALTER TABLE housekeeping_checklists
    DROP CONSTRAINT IF EXISTS housekeeping_checklists_service_request_id_key;

ALTER TABLE housekeeping_checklists
    ALTER COLUMN service_request_id DROP NOT NULL;

CREATE UNIQUE INDEX ux_housekeeping_checklists_service_request_id
    ON housekeeping_checklists(service_request_id)
    WHERE service_request_id IS NOT NULL;

--rollback DROP INDEX IF EXISTS ux_housekeeping_checklists_service_request_id;
--rollback ALTER TABLE housekeeping_checklists ALTER COLUMN service_request_id SET NOT NULL;
--rollback ALTER TABLE housekeeping_checklists ADD CONSTRAINT housekeeping_checklists_service_request_id_key UNIQUE (service_request_id);
