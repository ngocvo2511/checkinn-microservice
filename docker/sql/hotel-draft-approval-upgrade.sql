-- Expand the legacy enum CHECK to match HotelApprovalStatus and createHotel.
-- Review/apply to the hotel database before deploying DRAFT workflow support.
-- No row values are rewritten; transaction rollback preserves the old constraint
-- if validation fails. Smoke scripts do not apply schema changes automatically.
BEGIN;
ALTER TABLE hotels DROP CONSTRAINT IF EXISTS hotels_approved_status_check;
ALTER TABLE hotels ADD CONSTRAINT hotels_approved_status_check
    CHECK (approved_status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED'));
COMMIT;
