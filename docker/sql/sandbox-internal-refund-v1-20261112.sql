-- BOOKING database. Apply after Batch 1/1.1, before deployment. No historical state backfill.
BEGIN;
ALTER TABLE refund_requests ADD COLUMN IF NOT EXISTS execution_mode varchar(32);
ALTER TABLE refund_requests ADD COLUMN IF NOT EXISTS refunded_at timestamptz;
DO $$ DECLARE c record; BEGIN
 FOR c IN SELECT conname FROM pg_constraint WHERE conrelid='refund_requests'::regclass AND contype='c' AND pg_get_constraintdef(oid) LIKE '%status%' LOOP
 EXECUTE format('ALTER TABLE refund_requests DROP CONSTRAINT %I',c.conname); END LOOP;
 FOR c IN SELECT conname FROM pg_constraint WHERE conrelid='manual_refund_executions'::regclass AND contype='c' AND (pg_get_constraintdef(oid) LIKE '%execution_method%' OR pg_get_constraintdef(oid) LIKE '%state%') LOOP
 EXECUTE format('ALTER TABLE manual_refund_executions DROP CONSTRAINT %I',c.conname); END LOOP;
END $$;
ALTER TABLE refund_requests ADD CONSTRAINT refund_status_v12 CHECK(status IN ('REQUESTED','APPROVED_PENDING_EXECUTION','REJECTED','REFUNDED'));
ALTER TABLE manual_refund_executions ADD CONSTRAINT refund_execution_method_v12 CHECK(execution_method IN ('MANUAL','SANDBOX_INTERNAL'));
ALTER TABLE manual_refund_executions ADD CONSTRAINT refund_execution_state_v12 CHECK(state IN ('PENDING_MANUAL_REFUND','PENDING_VERIFICATION','NEEDS_MANUAL_REVIEW','REFUNDED'));
ALTER TABLE manual_refund_executions DROP CONSTRAINT IF EXISTS refund_demo_complete_v12;
ALTER TABLE manual_refund_executions ADD CONSTRAINT refund_demo_complete_v12 CHECK(state <> 'REFUNDED' OR execution_method='SANDBOX_INTERNAL');
ALTER TABLE manual_refund_executions ADD CONSTRAINT refund_pending_evidence_v12 CHECK(state <> 'PENDING_VERIFICATION' OR (external_reference IS NOT NULL AND evidence_reference IS NOT NULL AND submitted_by IS NOT NULL AND submitted_at IS NOT NULL));
COMMIT;
