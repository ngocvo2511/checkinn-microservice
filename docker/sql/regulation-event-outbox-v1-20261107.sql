-- REGULATIONS only. Additive; no historical events/backfill or financial policy changes.
BEGIN;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM regulations WHERE version IS NULL OR version<1) THEN
  RAISE EXCEPTION 'Regulation source versions require reviewed repair';
 END IF;
END $$;
CREATE TABLE IF NOT EXISTS regulation_event_outbox (
 id uuid PRIMARY KEY, regulation_key varchar(255) NOT NULL, source_version integer NOT NULL CHECK(source_version>0),
 exchange_name varchar(255) NOT NULL, payload jsonb NOT NULL,
 state varchar(16) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','DELIVERED')),
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0), last_error varchar(64),
 next_attempt_at timestamptz NOT NULL DEFAULT now(), created_at timestamptz NOT NULL DEFAULT now(), delivered_at timestamptz,
 UNIQUE(regulation_key,source_version)
);
CREATE INDEX IF NOT EXISTS regulation_event_due ON regulation_event_outbox(state,next_attempt_at);
CREATE INDEX IF NOT EXISTS regulation_event_pending ON regulation_event_outbox(regulation_key,source_version) WHERE state='PENDING';
COMMIT;
