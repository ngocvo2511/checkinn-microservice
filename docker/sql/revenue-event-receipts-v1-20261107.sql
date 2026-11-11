-- REVENUE only. Audit first; ambiguous historical duplicates require human review.
-- No record deletion, automatic winner selection or legacy payload backfill.
BEGIN;
SELECT pg_advisory_xact_lock(hashtextextended('checkinn-revenue-event-receipts-v1',0));
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM payment_records GROUP BY booking_id HAVING count(*)>1)
 OR EXISTS(SELECT 1 FROM booking_status_records GROUP BY booking_id HAVING count(*)>1)
 THEN RAISE EXCEPTION 'Review duplicate revenue projections before applying uniqueness'; END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS revenue_payment_booking_unique ON payment_records(booking_id);
CREATE UNIQUE INDEX IF NOT EXISTS revenue_status_booking_unique ON booking_status_records(booking_id);
CREATE TABLE IF NOT EXISTS revenue_event_receipts (
 id uuid PRIMARY KEY, kind varchar(16) NOT NULL CHECK(kind IN ('BOOKING','PAYMENT')),
 booking_id text, event_hash varchar(64) NOT NULL, event_at timestamp,
 disposition varchar(32) NOT NULL CHECK(disposition IN ('APPLIED','IGNORED','REVIEW_REQUIRED')),
 reason varchar(64) NOT NULL, payload jsonb NOT NULL,
 received_at timestamptz NOT NULL DEFAULT now(), UNIQUE(kind,event_hash)
);
CREATE INDEX IF NOT EXISTS revenue_event_review_queue ON revenue_event_receipts(disposition,received_at,id);
COMMIT;
