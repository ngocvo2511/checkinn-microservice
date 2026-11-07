-- HOTEL database only. Additive/replayable. No stock backfill or assumed approvals.
BEGIN;
SELECT pg_advisory_xact_lock(hashtextextended('checkinn-room-inventory-review-v1-20261107',0));
-- Legacy target IDs deliberately have no FK: audit survives target removal.
CREATE TABLE IF NOT EXISTS room_inventory_review_requests (
 id uuid PRIMARY KEY, room_id uuid NOT NULL, hotel_id uuid NOT NULL,
 previous_quantity integer, proposed_quantity integer NOT NULL CHECK (proposed_quantity > 0),
 source varchar(1000) NOT NULL CHECK (length(trim(source)) > 0),
 requested_by uuid NOT NULL, requested_at timestamptz NOT NULL DEFAULT now(),
 status varchar(16) NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED','SUPERSEDED')),
 reviewed_by uuid, reviewed_at timestamptz, review_reason varchar(1000)
);
CREATE TABLE IF NOT EXISTS room_inventory_review_states (
 room_id uuid PRIMARY KEY, revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
 latest_request_id uuid REFERENCES room_inventory_review_requests(id),
 approved_request_id uuid REFERENCES room_inventory_review_requests(id)
);
CREATE INDEX IF NOT EXISTS room_inventory_review_queue ON room_inventory_review_requests(status,requested_at,id);
CREATE INDEX IF NOT EXISTS room_inventory_review_room ON room_inventory_review_requests(room_id,requested_at,id);
COMMIT;
