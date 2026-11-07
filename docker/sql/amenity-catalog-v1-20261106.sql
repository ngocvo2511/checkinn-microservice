-- HOTEL database only. Additive, replayable; no catalog seeds or legacy label mapping.
BEGIN;
SELECT pg_advisory_xact_lock(hashtextextended('checkinn-amenity-catalog-v1-20261106',0));
CREATE TABLE IF NOT EXISTS amenity_catalog (
 code varchar(64) PRIMARY KEY CHECK (code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
 label varchar(255) NOT NULL CHECK (length(trim(label)) > 0),
 description varchar(1000), active boolean NOT NULL,
 revision bigint NOT NULL DEFAULT 0,
 created_by uuid NOT NULL, updated_by uuid NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
-- Target IDs deliberately have no FK to legacy rows: replacing raw amenities must
-- preserve the audit history. Effective reads additionally join the current raw row.
CREATE TABLE IF NOT EXISTS amenity_mapping_requests (
 id uuid PRIMARY KEY, hotel_id uuid NOT NULL, amenity_id uuid NOT NULL,
 title_snapshot varchar(255) NOT NULL, requested_by uuid NOT NULL,
 requested_at timestamptz NOT NULL DEFAULT now(), note varchar(1000),
 status varchar(16) NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED','SUPERSEDED')),
 reviewed_by uuid, reviewed_at timestamptz, review_reason varchar(1000)
);
CREATE TABLE IF NOT EXISTS amenity_mapping_request_codes (
 request_id uuid NOT NULL REFERENCES amenity_mapping_requests(id),
 code varchar(64) NOT NULL REFERENCES amenity_catalog(code),
 PRIMARY KEY(request_id,code)
);
CREATE TABLE IF NOT EXISTS amenity_mapping_states (
 amenity_id uuid PRIMARY KEY, hotel_id uuid NOT NULL, revision bigint NOT NULL DEFAULT 0,
 latest_request_id uuid REFERENCES amenity_mapping_requests(id),
 approved_request_id uuid REFERENCES amenity_mapping_requests(id)
);
CREATE INDEX IF NOT EXISTS amenity_mapping_requests_pending ON amenity_mapping_requests(status,requested_at,id);
CREATE INDEX IF NOT EXISTS amenity_mapping_states_hotel ON amenity_mapping_states(hotel_id);
COMMIT;
