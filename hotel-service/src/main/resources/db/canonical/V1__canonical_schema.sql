-- Canonical fresh-database bootstrap. Explicit opt-in only; never replay on legacy databases.

SET search_path TO public;

CREATE TABLE amenity_category_types (
    id uuid NOT NULL,
    name varchar(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (name)
);

CREATE TABLE hotel_amenities (
    id uuid NOT NULL,
    category_id uuid NOT NULL,
    title varchar(255) NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE hotel_amenity_categories (
    id uuid NOT NULL,
    hotel_id uuid NOT NULL,
    title varchar(255) NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE cities (
    id uuid NOT NULL,
    name varchar(255) NOT NULL,
    latitude double precision,
    longitude double precision,
    parent_id uuid,
    hotel_count integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (name)
);

CREATE TABLE provinces (
    id uuid NOT NULL,
    name varchar(255) NOT NULL,
    latitude double precision,
    longitude double precision,
    hotel_count integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (name)
);

CREATE TABLE hotel_accommodation_type_changes (
    id uuid NOT NULL,
    hotel_id uuid NOT NULL,
    requester_id uuid NOT NULL,
    previous_type varchar(32),
    requested_type varchar(32) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    reviewed_at timestamp(6) with time zone,
    reviewer_id uuid,
    rejection_reason varchar(1000),
    PRIMARY KEY (id),
    CHECK (previous_type IN ('HOTEL','VILLA','APARTMENT','GUEST_HOUSE')),
    CHECK (requested_type IN ('HOTEL','VILLA','APARTMENT','GUEST_HOUSE')),
    CHECK (status IN ('PENDING','APPROVED','REJECTED','SUPERSEDED')),
    CHECK (previous_type IS DISTINCT FROM requested_type),
    CHECK ((status IN ('PENDING','SUPERSEDED') AND reviewed_at IS NULL AND reviewer_id IS NULL AND rejection_reason IS NULL) OR (status='APPROVED' AND reviewed_at IS NOT NULL AND reviewer_id IS NOT NULL AND rejection_reason IS NULL) OR (status='REJECTED' AND reviewed_at IS NOT NULL AND reviewer_id IS NOT NULL AND rejection_reason IS NOT NULL AND length(trim(rejection_reason))>0))
);

CREATE TABLE hotels (
    id uuid NOT NULL,
    owner_id uuid NOT NULL,
    city_id uuid NOT NULL,
    name varchar(255) NOT NULL,
    description text,
    accommodation_type varchar(32),
    revision bigint DEFAULT 0 NOT NULL,
    star_rating smallint,
    address jsonb NOT NULL,
    contact_email varchar(100),
    contact_phone varchar(20),
    business_license_number varchar(50) NOT NULL,
    tax_id varchar(50),
    operation_license_number varchar(50),
    owner_identity_number varchar(50),
    business_license_file_path varchar(255),
    operation_license_file_path varchar(255),
    owner_identity_front_path varchar(255),
    owner_identity_back_path varchar(255),
    is_active boolean NOT NULL,
    approved_status varchar(20) NOT NULL,
    rejection_reason text,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT hotels_accommodation_type_allowed CHECK (accommodation_type IN ('HOTEL','VILLA','APARTMENT','GUEST_HOUSE')),
    CHECK (approved_status IN ('DRAFT','PENDING','APPROVED','REJECTED'))
);

CREATE TABLE media_assets (
    id uuid NOT NULL,
    target_id uuid NOT NULL,
    target_type varchar(20) NOT NULL,
    url varchar(500) NOT NULL,
    is_thumbnail boolean NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (id),
    CHECK (target_type IN ('HOTEL','ROOM_TYPE'))
);

CREATE TABLE hotel_policies (
    id uuid NOT NULL,
    hotel_id uuid NOT NULL,
    category_id uuid NOT NULL,
    content text NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE policy_category_types (
    id uuid NOT NULL,
    name varchar(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (name)
);

CREATE TABLE hotel_questions (
    id uuid NOT NULL,
    hotel_id uuid NOT NULL,
    question varchar(255) NOT NULL,
    answer text NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE hotel_reviews (
    id uuid NOT NULL,
    hotel_id uuid NOT NULL,
    guest_id uuid NOT NULL,
    booking_id uuid,
    rating numeric(38,2) NOT NULL,
    staff_rating double precision,
    amenities_rating double precision,
    cleanliness_rating double precision,
    comfort_rating double precision,
    value_for_money_rating double precision,
    location_rating double precision,
    title varchar(255),
    content text,
    helpful_count integer NOT NULL,
    unhelpful_count integer NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (hotel_id, guest_id, booking_id),
    CHECK (status IN ('PENDING','PUBLISHED','REJECTED'))
);

CREATE TABLE review_feedbacks (
    id uuid NOT NULL,
    review_id uuid NOT NULL,
    user_id uuid NOT NULL,
    feedback_type varchar(255) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone,
    PRIMARY KEY (id),
    UNIQUE (review_id, user_id),
    CHECK (feedback_type IN ('HELPFUL','UNHELPFUL'))
);

CREATE TABLE review_responses (
    id uuid NOT NULL,
    review_id uuid NOT NULL,
    owner_id uuid NOT NULL,
    content text NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (review_id)
);

CREATE TABLE room_availability (
    id uuid NOT NULL,
    room_type_id uuid NOT NULL,
    date date NOT NULL,
    held integer DEFAULT 0 NOT NULL,
    booked integer DEFAULT 0 NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (room_type_id, date),
    CHECK (held >= 0),
    CHECK (booked >= 0)
);

CREATE TABLE room_holds (
    id uuid NOT NULL,
    command_id uuid,
    room_type_id uuid NOT NULL,
    hotel_id uuid NOT NULL,
    check_in_date date NOT NULL,
    check_out_date date NOT NULL,
    quantity integer NOT NULL,
    status varchar(255) NOT NULL,
    expires_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    UNIQUE (command_id),
    PRIMARY KEY (id),
    CHECK (status IN ('HELD','CONFIRMED','RELEASED')),
    CHECK (quantity > 0),
    CHECK (check_out_date > check_in_date)
);

CREATE TABLE room_types (
    id uuid NOT NULL,
    hotel_id uuid NOT NULL,
    name varchar(100) NOT NULL,
    description text,
    base_price numeric(38,2) NOT NULL,
    capacity jsonb NOT NULL,
    amenities jsonb,
    is_active boolean NOT NULL,
    total_rooms integer,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE amenity_catalog (
    code varchar(64) NOT NULL,
    label varchar(255) NOT NULL,
    description varchar(1000),
    active boolean NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    created_by uuid NOT NULL,
    updated_by uuid NOT NULL,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    CHECK (code::text ~ '^[A-Z][A-Z0-9_]{0,63}$'::text),
    CHECK (length(TRIM(BOTH FROM label)) > 0),
    PRIMARY KEY (code)
);

CREATE TABLE amenity_mapping_requests (
    id uuid NOT NULL,
    hotel_id uuid NOT NULL,
    amenity_id uuid NOT NULL,
    title_snapshot varchar(255) NOT NULL,
    requested_by uuid NOT NULL,
    requested_at timestamptz DEFAULT now() NOT NULL,
    note varchar(1000),
    status varchar(16) NOT NULL,
    reviewed_by uuid,
    reviewed_at timestamptz,
    review_reason varchar(1000),
    PRIMARY KEY (id),
    CHECK (status::text = ANY (ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'SUPERSEDED'::character varying]::text[]))
);

CREATE TABLE amenity_mapping_request_codes (
    request_id uuid NOT NULL,
    code varchar(64) NOT NULL,
    PRIMARY KEY (request_id, code)
);

CREATE TABLE amenity_mapping_states (
    amenity_id uuid NOT NULL,
    hotel_id uuid NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    latest_request_id uuid,
    approved_request_id uuid,
    PRIMARY KEY (amenity_id)
);

CREATE TABLE checkinn_feature_migrations (
    migration_id varchar(100) NOT NULL,
    applied_at timestamptz DEFAULT now() NOT NULL,
    PRIMARY KEY (migration_id)
);

CREATE TABLE room_inventory_review_requests (
    id uuid NOT NULL,
    room_id uuid NOT NULL,
    hotel_id uuid NOT NULL,
    previous_quantity integer,
    proposed_quantity integer NOT NULL,
    source varchar(1000) NOT NULL,
    requested_by uuid NOT NULL,
    requested_at timestamptz DEFAULT now() NOT NULL,
    status varchar(16) NOT NULL,
    reviewed_by uuid,
    reviewed_at timestamptz,
    review_reason varchar(1000),
    PRIMARY KEY (id),
    CHECK (proposed_quantity > 0),
    CHECK (length(TRIM(BOTH FROM source)) > 0),
    CHECK (status::text = ANY (ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'SUPERSEDED'::character varying]::text[]))
);

CREATE TABLE room_inventory_review_states (
    room_id uuid NOT NULL,
    revision bigint DEFAULT 0 NOT NULL,
    latest_request_id uuid,
    approved_request_id uuid,
    PRIMARY KEY (room_id),
    CHECK (revision >= 0)
);

ALTER TABLE hotel_amenities ADD FOREIGN KEY (category_id) REFERENCES hotel_amenity_categories(id);

ALTER TABLE cities ADD FOREIGN KEY (parent_id) REFERENCES provinces(id);

ALTER TABLE hotel_accommodation_type_changes ADD FOREIGN KEY (hotel_id) REFERENCES hotels(id);

ALTER TABLE hotels ADD FOREIGN KEY (city_id) REFERENCES cities(id);

ALTER TABLE hotel_policies ADD FOREIGN KEY (category_id) REFERENCES policy_category_types(id);

ALTER TABLE hotel_policies ADD FOREIGN KEY (hotel_id) REFERENCES hotels(id);

ALTER TABLE hotel_questions ADD FOREIGN KEY (hotel_id) REFERENCES hotels(id);

ALTER TABLE review_feedbacks ADD FOREIGN KEY (review_id) REFERENCES hotel_reviews(id) ON DELETE CASCADE;

ALTER TABLE review_responses ADD FOREIGN KEY (review_id) REFERENCES hotel_reviews(id) ON DELETE CASCADE;

ALTER TABLE amenity_mapping_request_codes ADD FOREIGN KEY (code) REFERENCES amenity_catalog(code);

ALTER TABLE amenity_mapping_request_codes ADD FOREIGN KEY (request_id) REFERENCES amenity_mapping_requests(id);

ALTER TABLE amenity_mapping_states ADD FOREIGN KEY (approved_request_id) REFERENCES amenity_mapping_requests(id);

ALTER TABLE amenity_mapping_states ADD FOREIGN KEY (latest_request_id) REFERENCES amenity_mapping_requests(id);

ALTER TABLE room_inventory_review_states ADD FOREIGN KEY (approved_request_id) REFERENCES room_inventory_review_requests(id);

ALTER TABLE room_inventory_review_states ADD FOREIGN KEY (latest_request_id) REFERENCES room_inventory_review_requests(id);

CREATE INDEX hotel_amenities_category ON public.hotel_amenities (category_id);

CREATE INDEX amenity_categories_hotel ON public.hotel_amenity_categories (hotel_id);

CREATE INDEX accommodation_change_hotel_created ON public.hotel_accommodation_type_changes USING btree (hotel_id, created_at DESC);

CREATE UNIQUE INDEX accommodation_change_one_pending ON public.hotel_accommodation_type_changes USING btree (hotel_id) WHERE ((status)::text = 'PENDING'::text);

CREATE INDEX idx_hotels_city_id ON public.hotels (city_id);

CREATE INDEX hotels_owner ON public.hotels (owner_id);

CREATE INDEX idx_media_assets_target ON public.media_assets (target_id, target_type);

CREATE INDEX hotel_policies_hotel ON public.hotel_policies (hotel_id);

CREATE INDEX hotel_questions_hotel ON public.hotel_questions (hotel_id);

CREATE INDEX hotel_reviews_hotel_status_created ON public.hotel_reviews (hotel_id, status, created_at DESC);

CREATE INDEX room_holds_expiry ON public.room_holds (expires_at) WHERE status='HELD';

CREATE INDEX room_types_hotel ON public.room_types (hotel_id);

CREATE INDEX amenity_mapping_requests_pending ON public.amenity_mapping_requests USING btree (status, requested_at, id);

CREATE INDEX amenity_mapping_states_hotel ON public.amenity_mapping_states USING btree (hotel_id);

CREATE INDEX room_inventory_review_queue ON public.room_inventory_review_requests USING btree (status, requested_at, id);

CREATE INDEX room_inventory_review_room ON public.room_inventory_review_requests USING btree (room_id, requested_at, id);

INSERT INTO checkinn_feature_migrations(migration_id) VALUES ('hotel-accommodation-type-20261104');
