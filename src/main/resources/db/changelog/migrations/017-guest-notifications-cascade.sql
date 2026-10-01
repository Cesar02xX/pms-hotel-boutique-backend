--liquibase formatted sql

--changeset aurora:017-guest-notifications-cascade
ALTER TABLE guest_notifications
    DROP CONSTRAINT guest_notifications_booking_id_fkey,
    ADD CONSTRAINT guest_notifications_booking_id_fkey
        FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE;

ALTER TABLE guest_notifications
    DROP CONSTRAINT guest_notifications_guest_id_fkey,
    ADD CONSTRAINT guest_notifications_guest_id_fkey
        FOREIGN KEY (guest_id) REFERENCES guests(id) ON DELETE CASCADE;

--rollback ALTER TABLE guest_notifications DROP CONSTRAINT guest_notifications_booking_id_fkey;
--rollback ALTER TABLE guest_notifications ADD CONSTRAINT guest_notifications_booking_id_fkey FOREIGN KEY (booking_id) REFERENCES bookings(id);
--rollback ALTER TABLE guest_notifications DROP CONSTRAINT guest_notifications_guest_id_fkey;
--rollback ALTER TABLE guest_notifications ADD CONSTRAINT guest_notifications_guest_id_fkey FOREIGN KEY (guest_id) REFERENCES guests(id);
