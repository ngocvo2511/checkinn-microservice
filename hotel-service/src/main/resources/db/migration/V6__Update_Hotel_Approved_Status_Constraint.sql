-- Migration: Update approved_status constraint to include DRAFT
-- Allows newly created hotels to be stored as DRAFT before administrative approval

-- 1. Drop existing check constraints on approved_status in hotels table
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT conname
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attnum = ANY(c.conkey) AND a.attrelid = c.conrelid
        WHERE c.conrelid = 'hotels'::regclass
          AND c.contype = 'c'
          AND a.attname = 'approved_status'
    ) LOOP
        EXECUTE 'ALTER TABLE hotels DROP CONSTRAINT IF EXISTS ' || quote_ident(r.conname);
    END LOOP;
END$$;

-- 2. Add updated check constraint including 'DRAFT'
ALTER TABLE hotels ADD CONSTRAINT hotels_approved_status_check 
    CHECK (approved_status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED'));

-- 3. In case a PostgreSQL native enum type was created
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'hotel_approval_status') THEN
        ALTER TYPE hotel_approval_status ADD VALUE IF NOT EXISTS 'DRAFT';
    END IF;
END$$;
