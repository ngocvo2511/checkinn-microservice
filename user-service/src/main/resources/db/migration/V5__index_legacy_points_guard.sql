-- Read-side protection for ambiguous historical EARN/USE. No historical row is rewritten.
CREATE INDEX points_transactions_guard_user ON points_transactions (user_id, booking_id, transaction_type);
CREATE INDEX points_transactions_guard_booking ON points_transactions (booking_id, transaction_type);
