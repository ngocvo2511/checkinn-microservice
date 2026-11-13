-- REVENUE database, before consumer deployment. NULL denotes legacy/provider classification.
BEGIN;
ALTER TABLE revenue_cash_transactions ADD COLUMN IF NOT EXISTS execution_mode varchar(32);
COMMIT;
