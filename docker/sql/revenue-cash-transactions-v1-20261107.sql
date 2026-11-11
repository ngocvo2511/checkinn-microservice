-- REVENUE only. Immutable full-payment cash movements. Never derive money from latest projections.
BEGIN;
CREATE TABLE IF NOT EXISTS revenue_cash_transactions (
 id uuid PRIMARY KEY, source_event_id uuid NOT NULL UNIQUE, source_version bigint NOT NULL,
 payment_id varchar(255) NOT NULL, kind varchar(16) NOT NULL CHECK(kind IN ('CREDIT','DEBIT')),
 transaction_id varchar(255) NOT NULL, occurred_at timestamptz NOT NULL,
 booking_id varchar(255) NOT NULL, customer_id varchar(255) NOT NULL, hotel_id varchar(255) NOT NULL,
 room_type_id varchar(255), check_in_date date NOT NULL, check_out_date date NOT NULL,
 nights integer NOT NULL CHECK(nights>0), rooms integer NOT NULL CHECK(rooms>0),
 amount numeric NOT NULL CHECK(amount>0), payment_method varchar(255) NOT NULL,
 recorded_at timestamptz NOT NULL DEFAULT now(), UNIQUE(payment_id,kind)
);
CREATE INDEX IF NOT EXISTS revenue_cash_period ON revenue_cash_transactions(occurred_at,hotel_id);
COMMIT;
