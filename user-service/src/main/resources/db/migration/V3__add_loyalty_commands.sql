-- Additive command receipts. Existing accounts and ledger history are untouched.
CREATE TABLE loyalty_commands (
    command_key VARCHAR(160) PRIMARY KEY,
    booking_id VARCHAR(128) NOT NULL,
    command_type VARCHAR(16) NOT NULL,
    user_id UUID NOT NULL,
    input_amount NUMERIC,
    input_points BIGINT,
    result_amount NUMERIC,
    result_points BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT loyalty_commands_booking_type UNIQUE (booking_id, command_type),
    CONSTRAINT loyalty_commands_type CHECK (command_type IN ('EARN', 'USE', 'CANCEL'))
);
