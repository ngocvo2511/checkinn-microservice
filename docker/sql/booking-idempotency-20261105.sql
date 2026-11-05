-- Apply to the BOOKING database before the coordinated release. Additive; no backfill.
BEGIN;
SELECT pg_advisory_xact_lock(hashtextextended('checkinn-booking-idempotency-20261105', 0));
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS idempotency_key_hash varchar(64);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS idempotency_request_hash varchar(64);
CREATE UNIQUE INDEX IF NOT EXISTS bookings_idempotency_key_hash_unique ON bookings(idempotency_key_hash);
COMMIT;
