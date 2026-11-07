-- BOOKING only. Deploy before enabling the new producer. No historic event backfill.
BEGIN;
CREATE SEQUENCE IF NOT EXISTS booking_event_outbox_version_seq AS bigint NO CYCLE;
CREATE TABLE IF NOT EXISTS booking_event_outbox (
 id uuid PRIMARY KEY, booking_id varchar(255) NOT NULL, event_version bigint NOT NULL UNIQUE,
 exchange_name varchar(255) NOT NULL, routing_key varchar(128) NOT NULL,
 payload jsonb NOT NULL,
 state varchar(16) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','DELIVERED')),
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0),
 next_attempt_at timestamptz NOT NULL DEFAULT now(), last_error varchar(64),
 created_at timestamptz NOT NULL DEFAULT now(), delivered_at timestamptz
);
CREATE INDEX IF NOT EXISTS booking_event_due ON booking_event_outbox(state,next_attempt_at,event_version);
CREATE INDEX IF NOT EXISTS booking_event_stream_pending ON booking_event_outbox(booking_id,event_version) WHERE state='PENDING';
COMMIT;
