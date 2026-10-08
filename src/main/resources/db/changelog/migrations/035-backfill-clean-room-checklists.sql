--liquibase formatted sql

--changeset aurora:035-backfill-clean-room-checklists
-- A clean/inspected room from before turnover checklists existed is already
-- recorded as cleaned. Bring that legacy state into the checklist invariant.
UPDATE housekeeping_checklist_items item
SET checked = true,
    checked_at = COALESCE(item.checked_at, room.cleaning_completed_at, room.updated_at, now()),
    updated_at = now()
FROM housekeeping_checklists checklist
JOIN rooms room ON room.id = checklist.room_id
WHERE item.checklist_id = checklist.id
  AND checklist.service_request_id IS NULL
  AND checklist.status IN ('pending', 'in_progress')
  AND room.housekeeping_status IN ('clean', 'inspected');

UPDATE housekeeping_checklists checklist
SET status = 'completed',
    started_at = COALESCE(checklist.started_at, room.cleaning_started_at, room.updated_at, now()),
    completed_at = COALESCE(checklist.completed_at, room.cleaning_completed_at, room.updated_at, now()),
    updated_at = now()
FROM rooms room
WHERE room.id = checklist.room_id
  AND checklist.service_request_id IS NULL
  AND checklist.status IN ('pending', 'in_progress')
  AND room.housekeeping_status IN ('clean', 'inspected');

INSERT INTO housekeeping_checklists (room_id, status, started_at, completed_at, created_at, updated_at)
SELECT room.id,
       'completed',
       COALESCE(room.cleaning_started_at, room.updated_at, now()),
       COALESCE(room.cleaning_completed_at, room.updated_at, now()),
       COALESCE(room.cleaning_completed_at, room.updated_at, now()),
       now()
FROM rooms room
WHERE room.housekeeping_status IN ('clean', 'inspected')
  AND NOT EXISTS (
      SELECT 1
      FROM housekeeping_checklists checklist
      WHERE checklist.room_id = room.id
        AND checklist.service_request_id IS NULL
        AND checklist.status = 'completed'
  );

INSERT INTO housekeeping_checklist_items (checklist_id, label, checked, position, checked_at, created_at, updated_at)
SELECT checklist.id, item.label, true, item.position,
       COALESCE(checklist.completed_at, now()), now(), now()
FROM housekeeping_checklists checklist
JOIN rooms room ON room.id = checklist.room_id
CROSS JOIN (VALUES
    (0, 'Cama preparada'),
    (1, 'Bano limpio'),
    (2, 'Toallas completas'),
    (3, 'Amenidades repuestas'),
    (4, 'Basura retirada'),
    (5, 'Piso limpio')
) AS item(position, label)
WHERE checklist.service_request_id IS NULL
  AND checklist.status = 'completed'
  AND room.housekeeping_status IN ('clean', 'inspected')
  AND NOT EXISTS (
      SELECT 1 FROM housekeeping_checklist_items existing
      WHERE existing.checklist_id = checklist.id
  );
