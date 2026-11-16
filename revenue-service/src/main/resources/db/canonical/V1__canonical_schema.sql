-- Canonical fresh-database bootstrap. Explicit opt-in only; never replay on legacy databases.

SET search_path TO public;

CREATE TABLE booking_status_records (
    id uuid NOT NULL,
    booking_id uuid NOT NULL,
    hotel_id uuid NOT NULL,
    room_type_id uuid,
    check_in_date date NOT NULL,
    check_out_date date NOT NULL,
    nights integer NOT NULL,
    rooms integer NOT NULL,
    booking_status varchar(255) NOT NULL,
    event_at timestamp(6) without time zone NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    event_version bigint,
    last_event_id uuid,
    PRIMARY KEY (id)
);

CREATE TABLE payment_records (
    id uuid NOT NULL,
    booking_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    hotel_id uuid NOT NULL,
    room_type_id uuid,
    check_in_date date NOT NULL,
    check_out_date date NOT NULL,
    nights integer NOT NULL,
    rooms integer NOT NULL,
    amount numeric(38,2) NOT NULL,
    payment_status varchar(255) NOT NULL,
    payment_method varchar(255) NOT NULL,
    paid_at timestamp(6) without time zone,
    event_at timestamp(6) without time zone NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    event_version bigint,
    last_event_id uuid,
    PRIMARY KEY (id)
);

CREATE TABLE revenue_cash_transactions (
    id uuid NOT NULL,
    source_event_id uuid NOT NULL,
    source_version bigint NOT NULL,
    payment_id uuid NOT NULL,
    kind varchar(16) NOT NULL,
    transaction_id varchar(255) NOT NULL,
    occurred_at timestamptz NOT NULL,
    booking_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    hotel_id uuid NOT NULL,
    room_type_id uuid,
    check_in_date date NOT NULL,
    check_out_date date NOT NULL,
    nights integer NOT NULL,
    rooms integer NOT NULL,
    amount numeric NOT NULL,
    payment_method varchar(255) NOT NULL,
    recorded_at timestamptz DEFAULT now() NOT NULL,
    execution_mode varchar(32),
    CHECK (amount > 0::numeric),
    CHECK (kind::text = ANY (ARRAY['CREDIT'::character varying, 'DEBIT'::character varying]::text[])),
    CHECK (nights > 0),
    UNIQUE (payment_id, kind),
    PRIMARY KEY (id),
    CHECK (rooms > 0),
    UNIQUE (source_event_id)
);

CREATE TABLE revenue_event_receipts (
    id uuid NOT NULL,
    kind varchar(16) NOT NULL,
    booking_id text,
    event_hash varchar(64) NOT NULL,
    event_at timestamp,
    disposition varchar(32) NOT NULL,
    reason varchar(64) NOT NULL,
    payload jsonb NOT NULL,
    received_at timestamptz DEFAULT now() NOT NULL,
    CHECK (disposition::text = ANY (ARRAY['APPLIED'::character varying, 'IGNORED'::character varying, 'REVIEW_REQUIRED'::character varying]::text[])),
    CHECK (kind::text = ANY (ARRAY['BOOKING'::character varying, 'PAYMENT'::character varying]::text[])),
    UNIQUE (kind, event_hash),
    PRIMARY KEY (id)
);

CREATE TABLE revenue_cashflow_gaps (
    booking_id uuid NOT NULL,
    hotel_id uuid NOT NULL,
    source_record_id uuid,
    reason varchar(64) NOT NULL,
    detected_at timestamptz DEFAULT now() NOT NULL,
    resolved_at timestamptz,
    resolved_by uuid,
    resolution_evidence text,
    PRIMARY KEY (booking_id),
    CHECK (resolved_at IS NULL OR resolved_by IS NOT NULL AND resolution_evidence IS NOT NULL AND length(TRIM(BOTH FROM resolution_evidence)) > 0)
);

CREATE UNIQUE INDEX revenue_status_booking_unique ON public.booking_status_records USING btree (booking_id);

CREATE UNIQUE INDEX revenue_payment_booking_unique ON public.payment_records USING btree (booking_id);

CREATE INDEX revenue_cash_period ON public.revenue_cash_transactions USING btree (occurred_at, hotel_id);

CREATE INDEX revenue_event_review_queue ON public.revenue_event_receipts USING btree (disposition, received_at, id);

CREATE INDEX revenue_cashflow_gaps_scope ON public.revenue_cashflow_gaps USING btree (hotel_id) WHERE (resolved_at IS NULL);
