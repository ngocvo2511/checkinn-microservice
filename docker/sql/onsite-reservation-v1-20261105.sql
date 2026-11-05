-- BOOKING database; additive and replayable. No historical booking is promoted.
BEGIN;
SELECT pg_advisory_xact_lock(hashtextextended('checkinn-onsite-reservation-v1-20261105',0));
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS onsite_reservation_status varchar(24);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS onsite_next_attempt_at timestamp;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS onsite_attempts integer;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS onsite_last_error varchar(64);
CREATE INDEX IF NOT EXISTS bookings_onsite_due ON bookings(onsite_reservation_status,onsite_next_attempt_at);
COMMIT;
