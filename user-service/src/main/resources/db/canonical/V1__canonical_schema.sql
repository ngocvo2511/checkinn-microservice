-- Canonical fresh-database bootstrap. Explicit opt-in only; never replay on legacy databases.

SET search_path TO public;

CREATE TABLE loyalty_commands (
    command_key varchar(160) NOT NULL,
    booking_id uuid NOT NULL,
    command_type varchar(16) NOT NULL,
    user_id uuid NOT NULL,
    input_amount numeric,
    input_points bigint,
    result_amount numeric,
    result_points bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    UNIQUE (booking_id, command_type),
    PRIMARY KEY (command_key),
    CHECK (command_type::text = ANY (ARRAY['EARN'::character varying, 'USE'::character varying, 'CANCEL'::character varying]::text[]))
);

CREATE TABLE loyalty_points (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    total_points bigint NOT NULL,
    used_points bigint NOT NULL,
    available_points bigint NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE otps (
    id uuid NOT NULL,
    email varchar(255) NOT NULL,
    otp_code varchar(255) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    expiry_time timestamp(6) without time zone NOT NULL,
    is_verified boolean NOT NULL,
    attempt_count integer NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE points_reservations (
    booking_id uuid NOT NULL,
    user_id uuid NOT NULL,
    requested_points bigint NOT NULL,
    total_amount numeric NOT NULL,
    points bigint NOT NULL,
    discount_amount numeric NOT NULL,
    redemption_rate numeric NOT NULL,
    state varchar(16) NOT NULL,
    hold_expires_at timestamp(6) with time zone NOT NULL,
    next_check_at timestamp(6) with time zone NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CHECK (discount_amount >= 0::numeric),
    PRIMARY KEY (booking_id),
    CHECK (points >= 0),
    CHECK (redemption_rate > 0::numeric),
    CHECK (state IN ('HELD','COMMITTED','CANCELLED'))
);

CREATE TABLE points_transactions (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    transaction_type varchar(255) NOT NULL,
    points bigint NOT NULL,
    booking_id uuid,
    description varchar(500) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE users (
    id uuid NOT NULL,
    username varchar(50) NOT NULL,
    email varchar(254) NOT NULL,
    password varchar(100) NOT NULL,
    role varchar(20) NOT NULL,
    is_email_verified boolean NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    UNIQUE (email),
    PRIMARY KEY (id),
    UNIQUE (username),
    CHECK (role IN ('ADMIN','STAFF','CUSTOMER','OWNER'))
);

CREATE TABLE user_profiles (
    id uuid NOT NULL,
    full_name varchar(100) NOT NULL,
    gender varchar(255),
    birth_date date,
    phone varchar(255),
    address varchar(255),
    country varchar(255),
    user_id uuid NOT NULL,
    loyalty_points_id uuid,
    UNIQUE (loyalty_points_id),
    UNIQUE (user_id),
    PRIMARY KEY (id)
);

ALTER TABLE user_profiles ADD FOREIGN KEY (loyalty_points_id) REFERENCES loyalty_points(id);

ALTER TABLE user_profiles ADD FOREIGN KEY (user_id) REFERENCES users(id);

CREATE INDEX loyalty_points_user ON public.loyalty_points (user_id);

CREATE INDEX otps_email_created ON public.otps (email, created_at DESC);

CREATE INDEX points_reservations_held_user ON public.points_reservations USING btree (user_id) WHERE ((state)::text = 'HELD'::text);

CREATE INDEX points_reservations_recovery ON public.points_reservations USING btree (next_check_at) WHERE ((state)::text = 'HELD'::text);

CREATE INDEX points_transactions_guard_booking ON public.points_transactions USING btree (booking_id, transaction_type);

CREATE INDEX points_transactions_guard_user ON public.points_transactions USING btree (user_id, booking_id, transaction_type);

CREATE INDEX points_transactions_user_created ON public.points_transactions (user_id, created_at DESC);
