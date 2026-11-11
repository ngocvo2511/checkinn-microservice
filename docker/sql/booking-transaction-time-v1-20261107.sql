-- BOOKING only. Apply before deploying the transaction-day cash event producer.
-- Never infer provider transaction time from legacy paid_at/created_at or callback arrival.
BEGIN;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS transaction_occurred_at timestamptz;
COMMIT;
