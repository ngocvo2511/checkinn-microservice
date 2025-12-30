CREATE TABLE IF NOT EXISTS room_availability (
    id UUID PRIMARY KEY,
    room_type_id UUID NOT NULL,
    date DATE NOT NULL,
    held INTEGER NOT NULL DEFAULT 0,
    booked INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_room_type_date UNIQUE (room_type_id, date)
);

CREATE TABLE IF NOT EXISTS room_holds (
    id UUID PRIMARY KEY,
    room_type_id UUID NOT NULL,
    hotel_id UUID NOT NULL,
    check_in_date DATE NOT NULL,
    check_out_date DATE NOT NULL,
    quantity INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
