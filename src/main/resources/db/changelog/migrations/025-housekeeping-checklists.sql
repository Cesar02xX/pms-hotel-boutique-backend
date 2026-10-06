--liquibase formatted sql

--changeset aurora:025-housekeeping-checklists
CREATE TABLE housekeeping_checklists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_request_id UUID NOT NULL UNIQUE REFERENCES service_requests(id),
    room_id UUID NOT NULL REFERENCES rooms(id),
    responsible_user_id UUID REFERENCES users(id),
    completed_by_user_id UUID REFERENCES users(id),
    status VARCHAR NOT NULL,
    observations TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_housekeeping_checklists_status CHECK (status IN ('pending', 'in_progress', 'completed', 'cancelled'))
);

CREATE TABLE housekeeping_checklist_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checklist_id UUID NOT NULL REFERENCES housekeeping_checklists(id) ON DELETE CASCADE,
    label TEXT NOT NULL,
    checked BOOLEAN NOT NULL DEFAULT FALSE,
    position INTEGER NOT NULL,
    notes TEXT,
    checked_at TIMESTAMPTZ,
    checked_by_user_id UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_housekeeping_checklists_room_id ON housekeeping_checklists(room_id);
CREATE INDEX idx_housekeeping_checklists_status ON housekeeping_checklists(status);
CREATE INDEX idx_housekeeping_checklists_responsible_user_id ON housekeeping_checklists(responsible_user_id);
CREATE INDEX idx_housekeeping_checklist_items_checklist_id ON housekeeping_checklist_items(checklist_id);

--rollback DROP TABLE IF EXISTS housekeeping_checklist_items;
--rollback DROP TABLE IF EXISTS housekeeping_checklists;
