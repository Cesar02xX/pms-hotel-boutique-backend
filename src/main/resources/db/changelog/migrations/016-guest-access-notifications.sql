--liquibase formatted sql

--changeset aurora:016-guest-access-notifications
CREATE TABLE guest_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    guest_id UUID NOT NULL REFERENCES guests(id),
    type VARCHAR NOT NULL,
    title VARCHAR NOT NULL,
    message TEXT NOT NULL,
    resource_type VARCHAR,
    resource_id UUID,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_guest_notifications_booking_created
    ON guest_notifications(booking_id, created_at DESC);

CREATE INDEX idx_guest_notifications_unread
    ON guest_notifications(booking_id)
    WHERE read_at IS NULL;

CREATE UNIQUE INDEX ux_guest_notifications_resource_type
    ON guest_notifications(booking_id, resource_type, resource_id, type)
    WHERE resource_type IS NOT NULL AND resource_id IS NOT NULL;

--rollback DROP TABLE IF EXISTS guest_notifications;
