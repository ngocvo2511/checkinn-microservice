-- BOOKING database, additive/replayable. NULL preserves historical receipt provenance.
BEGIN;
SELECT pg_advisory_xact_lock(hashtextextended('checkinn-paid-reservation-v1-20261105',0));
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS paid_reservation_status varchar(24);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS paid_reservation_payment_id varchar(255);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS paid_reservation_next_attempt_at timestamp;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS paid_reservation_attempts integer;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS paid_reservation_last_error varchar(64);
CREATE INDEX IF NOT EXISTS bookings_paid_reservation_due ON bookings(paid_reservation_status,paid_reservation_next_attempt_at);
COMMIT;
