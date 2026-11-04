-- Explicit deployment migration. Not a Flyway resource; execute with psql ON_ERROR_STOP.
-- Apply before starting the feature build. No classification backfill; legacy NULL is retained.
BEGIN;
SET LOCAL lock_timeout = '15s';
SELECT pg_advisory_xact_lock(6142104112026);
CREATE TABLE IF NOT EXISTS checkinn_feature_migrations (
    migration_id varchar(100) PRIMARY KEY,
    applied_at timestamptz NOT NULL DEFAULT now()
);
DO $migration$
BEGIN
    IF EXISTS (SELECT 1 FROM checkinn_feature_migrations WHERE migration_id='hotel-accommodation-type-20261104') THEN
        RETURN;
    END IF;
    ALTER TABLE hotels ADD COLUMN accommodation_type varchar(32);
    ALTER TABLE hotels ADD COLUMN revision bigint NOT NULL DEFAULT 0;
    ALTER TABLE hotels ADD CONSTRAINT hotels_accommodation_type_allowed
        CHECK (accommodation_type IN ('HOTEL','VILLA','APARTMENT','GUEST_HOUSE')) NOT VALID;
    ALTER TABLE hotels VALIDATE CONSTRAINT hotels_accommodation_type_allowed;
    CREATE TABLE hotel_accommodation_type_changes (
        id uuid PRIMARY KEY,
        hotel_id uuid NOT NULL REFERENCES hotels(id),
        requester_id uuid NOT NULL,
        previous_type varchar(32),
        requested_type varchar(32) NOT NULL,
        status varchar(20) NOT NULL,
        created_at timestamptz NOT NULL,
        reviewed_at timestamptz,
        reviewer_id uuid,
        rejection_reason varchar(1000),
        CONSTRAINT accommodation_change_previous_allowed CHECK (previous_type IN ('HOTEL','VILLA','APARTMENT','GUEST_HOUSE')),
        CONSTRAINT accommodation_change_requested_allowed CHECK (requested_type IN ('HOTEL','VILLA','APARTMENT','GUEST_HOUSE')),
        CONSTRAINT accommodation_change_status_allowed CHECK (status IN ('PENDING','APPROVED','REJECTED','SUPERSEDED')),
        CONSTRAINT accommodation_change_distinct CHECK (previous_type IS DISTINCT FROM requested_type),
        CONSTRAINT accommodation_change_review_metadata CHECK (
            (status IN ('PENDING','SUPERSEDED') AND reviewed_at IS NULL AND reviewer_id IS NULL AND rejection_reason IS NULL)
            OR (status='APPROVED' AND reviewed_at IS NOT NULL AND reviewer_id IS NOT NULL AND rejection_reason IS NULL)
            OR (status='REJECTED' AND reviewed_at IS NOT NULL AND reviewer_id IS NOT NULL AND length(trim(rejection_reason))>0 AND rejection_reason IS NOT NULL)
        )
    );
    CREATE UNIQUE INDEX accommodation_change_one_pending ON hotel_accommodation_type_changes(hotel_id) WHERE status='PENDING';
    CREATE INDEX accommodation_change_hotel_created ON hotel_accommodation_type_changes(hotel_id,created_at DESC);
    INSERT INTO checkinn_feature_migrations(migration_id) VALUES ('hotel-accommodation-type-20261104');
END $migration$;
COMMIT;
