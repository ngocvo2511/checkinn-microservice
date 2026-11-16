-- Canonical fresh-database bootstrap. Explicit opt-in only; never replay on legacy databases.

SET search_path TO public;

CREATE SEQUENCE booking_event_outbox_version_seq AS bigint;

CREATE TABLE bookings (
    id uuid NOT NULL,
    idempotency_key_hash varchar(64),
    idempotency_request_hash varchar(64),
    user_id uuid NOT NULL,
    hotel_id uuid NOT NULL,
    hotel_name varchar(255) NOT NULL,
    check_in_date date NOT NULL,
    check_out_date date NOT NULL,
    adults integer NOT NULL,
    children integer NOT NULL,
    status varchar(255) NOT NULL,
    total_amount numeric(38,2) NOT NULL,
    paid_amount numeric(38,2) NOT NULL,
    voucher_code varchar(255),
    voucher_discount numeric(38,2),
    earned_points bigint,
    used_points bigint,
    points_discount_amount numeric(38,2),
    contact_name varchar(255) NOT NULL,
    contact_email varchar(255) NOT NULL,
    contact_phone varchar(255) NOT NULL,
    special_requests varchar(255),
    hold_id uuid,
    hold_expires_at timestamp(6) without time zone,
    cancellation_policy_version varchar(64),
    check_in_at timestamp(6) with time zone,
    free_cancellation_until timestamp(6) with time zone,
    loyalty_status varchar(24),
    loyalty_next_attempt_at timestamp(6) without time zone,
    loyalty_attempts integer,
    loyalty_last_error varchar(64),
    onsite_reservation_status varchar(24),
    onsite_next_attempt_at timestamp(6) without time zone,
    onsite_attempts integer,
    onsite_last_error varchar(64),
    paid_reservation_status varchar(24),
    paid_reservation_payment_id uuid,
    paid_reservation_next_attempt_at timestamp(6) without time zone,
    paid_reservation_attempts integer,
    paid_reservation_last_error varchar(64),
    inventory_reviewed_by uuid,
    inventory_review_reason varchar(1000),
    inventory_reviewed_at timestamp(6) with time zone,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    UNIQUE (idempotency_key_hash),
    PRIMARY KEY (id),
    CHECK (status IN ('PENDING','PENDING_PAYMENT','CONFIRMED','CHECKED_IN','CHECKED_OUT','CANCELLED','NO_SHOW')),
    CHECK (check_out_date > check_in_date),
    CHECK (adults >= 0 AND children >= 0),
    CHECK (total_amount >= 0 AND paid_amount >= 0)
);

CREATE TABLE booking_items (
    id uuid NOT NULL,
    booking_id uuid NOT NULL,
    room_type_id uuid NOT NULL,
    room_type_name varchar(255) NOT NULL,
    rate_plan_id varchar(255) NOT NULL,
    check_in_date date NOT NULL,
    check_out_date date NOT NULL,
    quantity integer NOT NULL,
    unit_price numeric(38,2) NOT NULL,
    nights integer NOT NULL,
    subtotal numeric(38,2) NOT NULL,
    tax_fee numeric(38,2),
    cancellation_policy varchar(255),
    guest_name varchar(255),
    PRIMARY KEY (id),
    CHECK (quantity > 0 AND nights > 0),
    CHECK (check_out_date > check_in_date),
    CHECK (unit_price >= 0 AND subtotal >= 0)
);

CREATE TABLE payments (
    id uuid NOT NULL,
    booking_id uuid NOT NULL,
    amount numeric(38,2) NOT NULL,
    method varchar(255) NOT NULL,
    status varchar(255) NOT NULL,
    transaction_id varchar(255),
    vnpay_order_id varchar(255),
    vnpay_create_date varchar(14),
    vnpay_response_code varchar(255),
    vnpay_transaction_no varchar(255),
    paid_at timestamp(6) without time zone,
    transaction_occurred_at timestamp(6) with time zone,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (vnpay_order_id),
    UNIQUE (vnpay_transaction_no),
    CHECK (method IN ('HOTEL','VNPAY')),
    CHECK (status IN ('PENDING','ONSITE_PENDING','COMPLETED','FAILED','REFUNDED','CANCELLED')),
    CHECK (amount >= 0)
);

CREATE TABLE refund_requests (
    id uuid NOT NULL,
    payment_id uuid NOT NULL,
    booking_id uuid NOT NULL,
    amount numeric(38,2) NOT NULL,
    reason varchar(1000) NOT NULL,
    requested_by uuid NOT NULL,
    approved_by uuid,
    status varchar(64) NOT NULL,
    requested_at timestamp(6) with time zone NOT NULL,
    approved_at timestamp(6) with time zone,
    execution_mode varchar(32),
    refunded_at timestamp(6) with time zone,
    version bigint DEFAULT 0 NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (payment_id),
    CHECK (status IN ('REQUESTED','APPROVED_PENDING_EXECUTION','REJECTED','REFUNDED')),
    CHECK (amount > 0)
);

CREATE TABLE booking_event_outbox (
    id uuid NOT NULL,
    booking_id uuid NOT NULL,
    event_version bigint NOT NULL,
    exchange_name varchar(255) NOT NULL,
    routing_key varchar(128) NOT NULL,
    payload jsonb NOT NULL,
    state varchar(16) DEFAULT 'PENDING' NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamptz DEFAULT now() NOT NULL,
    last_error varchar(64),
    created_at timestamptz DEFAULT now() NOT NULL,
    delivered_at timestamptz,
    CHECK (attempts >= 0),
    UNIQUE (event_version),
    PRIMARY KEY (id),
    CHECK (state::text = ANY (ARRAY['PENDING'::character varying, 'DELIVERED'::character varying]::text[]))
);

CREATE TABLE manual_refund_executions (
    id uuid NOT NULL,
    request_id uuid NOT NULL,
    payment_id uuid NOT NULL,
    booking_id uuid NOT NULL,
    amount numeric(38,2) NOT NULL,
    currency varchar(3) NOT NULL,
    provider varchar(32) NOT NULL,
    execution_method varchar(16) DEFAULT 'MANUAL' NOT NULL,
    state varchar(64) NOT NULL,
    external_reference varchar(200),
    evidence_reference varchar(200),
    submitted_by uuid,
    submitted_at timestamptz,
    created_at timestamptz DEFAULT now() NOT NULL,
    updated_at timestamptz DEFAULT now() NOT NULL,
    CHECK (amount > 0::numeric),
    CHECK (currency::text = 'VND'::text),
    UNIQUE (payment_id),
    PRIMARY KEY (id),
    UNIQUE (request_id),
    CHECK (state::text <> 'REFUNDED'::text OR execution_method::text = 'SANDBOX_INTERNAL'::text),
    CHECK (execution_method::text = ANY (ARRAY['MANUAL'::character varying, 'SANDBOX_INTERNAL'::character varying]::text[])),
    CHECK (state::text = ANY (ARRAY['PENDING_MANUAL_REFUND'::character varying, 'PENDING_VERIFICATION'::character varying, 'NEEDS_MANUAL_REVIEW'::character varying, 'REFUNDED'::character varying]::text[])),
    CHECK (state::text <> 'PENDING_VERIFICATION'::text OR external_reference IS NOT NULL AND evidence_reference IS NOT NULL AND submitted_by IS NOT NULL AND submitted_at IS NOT NULL)
);

CREATE TABLE manual_refund_audit (
    id uuid NOT NULL,
    execution_id uuid NOT NULL,
    action varchar(64) NOT NULL,
    actor_id varchar(255) NOT NULL,
    occurred_at timestamptz DEFAULT now() NOT NULL,
    PRIMARY KEY (id)
);

ALTER TABLE booking_items ADD FOREIGN KEY (booking_id) REFERENCES bookings(id);

ALTER TABLE refund_requests ADD FOREIGN KEY (payment_id) REFERENCES payments(id);

ALTER TABLE refund_requests ADD FOREIGN KEY (booking_id) REFERENCES bookings(id);

ALTER TABLE manual_refund_executions ADD FOREIGN KEY (booking_id) REFERENCES bookings(id);

ALTER TABLE manual_refund_executions ADD FOREIGN KEY (payment_id) REFERENCES payments(id);

ALTER TABLE manual_refund_executions ADD FOREIGN KEY (request_id) REFERENCES refund_requests(id);

ALTER TABLE manual_refund_audit ADD FOREIGN KEY (execution_id) REFERENCES manual_refund_executions(id);

CREATE INDEX bookings_loyalty_due ON public.bookings USING btree (loyalty_status, loyalty_next_attempt_at);

CREATE INDEX bookings_onsite_due ON public.bookings USING btree (onsite_reservation_status, onsite_next_attempt_at);

CREATE INDEX bookings_paid_reservation_due ON public.bookings USING btree (paid_reservation_status, paid_reservation_next_attempt_at);

CREATE INDEX bookings_user ON public.bookings (user_id);

CREATE INDEX bookings_hotel ON public.bookings (hotel_id);

CREATE INDEX bookings_status ON public.bookings (status);

CREATE INDEX bookings_created ON public.bookings (created_at);

CREATE INDEX bookings_check_in ON public.bookings (check_in_date);

CREATE INDEX booking_items_booking ON public.booking_items (booking_id);

CREATE INDEX payments_booking_created ON public.payments (booking_id, created_at DESC);

CREATE INDEX refund_requests_recovery ON public.refund_requests (approved_at) WHERE status='APPROVED_PENDING_EXECUTION';

CREATE INDEX booking_event_due ON public.booking_event_outbox USING btree (state, next_attempt_at, event_version);

CREATE INDEX booking_event_stream_pending ON public.booking_event_outbox USING btree (booking_id, event_version) WHERE ((state)::text = 'PENDING'::text);

CREATE INDEX manual_refund_audit_execution ON public.manual_refund_audit (execution_id, occurred_at);
