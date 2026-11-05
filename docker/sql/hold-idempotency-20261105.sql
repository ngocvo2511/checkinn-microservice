-- Apply to the HOTEL database before the coordinated release. Additive; no backfill.
BEGIN;
SELECT pg_advisory_xact_lock(hashtextextended('checkinn-hold-idempotency-20261105', 0));
ALTER TABLE room_holds ADD COLUMN IF NOT EXISTS command_id uuid;
CREATE UNIQUE INDEX IF NOT EXISTS room_holds_command_id_unique ON room_holds(command_id);
COMMIT;
