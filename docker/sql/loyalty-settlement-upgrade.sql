-- Additive booking-service schema change, supplied for reviewed deployments.
-- Test clone uses the service's existing ddl-auto=update. Do not backfill
-- historical financial work or run this against production from a smoke test.
BEGIN;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS loyalty_status VARCHAR(24);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS loyalty_next_attempt_at TIMESTAMP;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS loyalty_attempts INTEGER;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS loyalty_last_error VARCHAR(64);
CREATE INDEX IF NOT EXISTS bookings_loyalty_due ON bookings(loyalty_status,loyalty_next_attempt_at);
COMMIT;
