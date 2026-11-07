-- REVENUE only. Preserve evidence of incomplete history independently of mutable projections.
-- This classifies unknown history; it creates NO financial movement or guessed amount/date.
BEGIN;
CREATE TABLE IF NOT EXISTS revenue_cashflow_gaps (
 booking_id varchar(255) PRIMARY KEY, hotel_id varchar(255) NOT NULL,
 source_record_id uuid, reason varchar(64) NOT NULL,
 detected_at timestamptz NOT NULL DEFAULT now(),
 resolved_at timestamptz, resolved_by uuid, resolution_evidence text,
 CONSTRAINT revenue_gap_resolution_evidence CHECK(resolved_at IS NULL OR
   (resolved_by IS NOT NULL AND resolution_evidence IS NOT NULL AND length(trim(resolution_evidence))>0))
);
-- Add the explicit evidence check to an earlier local draft without removing any old guard.
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='revenue_cashflow_gaps'::regclass AND conname='revenue_gap_resolution_evidence') THEN
  ALTER TABLE revenue_cashflow_gaps ADD CONSTRAINT revenue_gap_resolution_evidence CHECK(resolved_at IS NULL OR
   (resolved_by IS NOT NULL AND resolution_evidence IS NOT NULL AND length(trim(resolution_evidence))>0));
 END IF;
END $$;
INSERT INTO revenue_cashflow_gaps(booking_id,hotel_id,source_record_id,reason)
 SELECT booking_id,hotel_id,id,'LEGACY_HISTORY_NOT_VERIFIED' FROM payment_records WHERE event_version IS NULL
 ON CONFLICT(booking_id) DO NOTHING;
CREATE INDEX IF NOT EXISTS revenue_cashflow_gaps_scope ON revenue_cashflow_gaps(hotel_id) WHERE resolved_at IS NULL;
COMMIT;
