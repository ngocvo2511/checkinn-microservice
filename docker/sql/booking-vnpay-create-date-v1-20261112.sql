-- BOOKING only; apply before deploying the new payment producer.
-- No backfill: vnp_CreateDate is not paid_at, created_at or vnp_PayDate.
BEGIN;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS vnpay_create_date varchar(14);
COMMIT;
