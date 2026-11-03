-- New holds only. Existing balances and journal entries are not rewritten.
CREATE TABLE points_reservations (
    booking_id VARCHAR(128) PRIMARY KEY,
    user_id UUID NOT NULL,
    requested_points BIGINT NOT NULL,
    total_amount NUMERIC NOT NULL,
    points BIGINT NOT NULL CHECK (points >= 0),
    discount_amount NUMERIC NOT NULL CHECK (discount_amount >= 0),
    redemption_rate NUMERIC NOT NULL CHECK (redemption_rate > 0),
    state VARCHAR(16) NOT NULL CHECK (state IN ('HELD', 'COMMITTED', 'CANCELLED')),
    hold_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    next_check_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX points_reservations_held_user ON points_reservations (user_id) WHERE state = 'HELD';
CREATE INDEX points_reservations_recovery ON points_reservations (next_check_at) WHERE state = 'HELD';
