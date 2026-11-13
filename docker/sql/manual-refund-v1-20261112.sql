BEGIN;
CREATE TABLE IF NOT EXISTS manual_refund_executions (
 id uuid PRIMARY KEY, request_id varchar(255) NOT NULL UNIQUE REFERENCES refund_requests(id),
 payment_id varchar(255) NOT NULL UNIQUE REFERENCES payments(id), booking_id varchar(255) NOT NULL REFERENCES bookings(id),
 amount numeric(38,2) NOT NULL CHECK(amount>0), currency varchar(3) NOT NULL CHECK(currency='VND'),
 provider varchar(32) NOT NULL, execution_method varchar(16) NOT NULL DEFAULT 'MANUAL' CHECK(execution_method='MANUAL'),
 state varchar(64) NOT NULL CHECK(state IN ('PENDING_MANUAL_REFUND','PENDING_VERIFICATION','NEEDS_MANUAL_REVIEW')),
 external_reference varchar(200), evidence_reference varchar(200), submitted_by varchar(255),submitted_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK(state <> 'PENDING_VERIFICATION' OR (external_reference IS NOT NULL AND evidence_reference IS NOT NULL AND submitted_by IS NOT NULL AND submitted_at IS NOT NULL))
);
CREATE TABLE IF NOT EXISTS manual_refund_audit (
 id uuid PRIMARY KEY, execution_id uuid NOT NULL REFERENCES manual_refund_executions(id),
 action varchar(64) NOT NULL, actor_id varchar(255) NOT NULL, occurred_at timestamptz NOT NULL DEFAULT now()
);
COMMIT;
