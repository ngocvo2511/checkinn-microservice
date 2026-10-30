-- Additive only. No historical booking is assigned a policy retroactively.
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS cancellation_policy_version varchar(64);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS check_in_at timestamp with time zone;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS free_cancellation_until timestamp with time zone;
CREATE TABLE IF NOT EXISTS refund_requests (
    id varchar(255) PRIMARY KEY,
    payment_id varchar(255) NOT NULL REFERENCES payments(id),
    booking_id varchar(255) NOT NULL REFERENCES bookings(id),
    amount numeric(38,2) NOT NULL CHECK (amount > 0),
    reason varchar(1000) NOT NULL,
    requested_by varchar(255) NOT NULL,
    approved_by varchar(255),
    status varchar(64) NOT NULL CHECK (status IN ('REQUESTED', 'APPROVED_PENDING_EXECUTION')),
    requested_at timestamp with time zone NOT NULL,
    approved_at timestamp with time zone,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_refund_requests_payment_id UNIQUE (payment_id)
);
