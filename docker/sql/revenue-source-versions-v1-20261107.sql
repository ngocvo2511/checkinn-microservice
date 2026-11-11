-- REVENUE only. Keep legacy NULLs; never invent an event identity/order.
BEGIN;
ALTER TABLE payment_records ADD COLUMN IF NOT EXISTS event_version bigint;
ALTER TABLE payment_records ADD COLUMN IF NOT EXISTS last_event_id uuid;
ALTER TABLE booking_status_records ADD COLUMN IF NOT EXISTS event_version bigint;
ALTER TABLE booking_status_records ADD COLUMN IF NOT EXISTS last_event_id uuid;
COMMIT;
