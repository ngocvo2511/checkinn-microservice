-- Apply to the HOTEL database before releasing the room-description writer.
BEGIN;
SELECT pg_advisory_xact_lock(hashtextextended('checkinn-room-description-20261105', 0));
ALTER TABLE room_types ADD COLUMN IF NOT EXISTS description text;
COMMIT;
