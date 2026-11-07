-- BOOKING database. Explicit ADMIN review metadata, no legacy/source backfill.
BEGIN;
SELECT pg_advisory_xact_lock(hashtextextended('checkinn-inventory-review-audit-20261106',0));
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS inventory_reviewed_by varchar(255);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS inventory_review_reason varchar(1000);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS inventory_reviewed_at timestamp with time zone;
COMMIT;
