ALTER TABLE posts
    ADD COLUMN hotel_id        UUID,
    ADD COLUMN stay_booking_id UUID,
    ADD COLUMN verified_stay   BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE posts
    ADD CONSTRAINT ck_posts_stay_needs_hotel CHECK (stay_booking_id IS NULL OR hotel_id IS NOT NULL),
    ADD CONSTRAINT ck_posts_verified_matches_stay CHECK (verified_stay = (stay_booking_id IS NOT NULL)),
    ADD CONSTRAINT ck_posts_no_rating_with_hotel CHECK (rating IS NULL OR hotel_id IS NULL);

CREATE UNIQUE INDEX ux_posts_live_stay ON posts (stay_booking_id)
    WHERE stay_booking_id IS NOT NULL AND status <> 'DELETED';
CREATE INDEX idx_posts_hotel_feed ON posts (hotel_id, status, created_at DESC, id DESC)
    WHERE hotel_id IS NOT NULL;

CREATE TABLE stay_invitations (
    booking_id        UUID PRIMARY KEY,
    user_id           UUID        NOT NULL,
    hotel_id          UUID        NOT NULL,
    check_out_date    DATE        NOT NULL,
    status            VARCHAR(16) NOT NULL,
    post_id           UUID,
    created_at        TIMESTAMPTZ NOT NULL,
    expires_at        TIMESTAMPTZ NOT NULL,
    status_changed_at TIMESTAMPTZ NOT NULL,
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_stay_invitations_status CHECK (status IN ('OPEN', 'USED', 'DISMISSED', 'EXPIRED')),
    CONSTRAINT ck_stay_invitations_used_has_post CHECK (status <> 'USED' OR post_id IS NOT NULL)
);

CREATE INDEX idx_stay_invitations_owner ON stay_invitations (user_id, status, check_out_date DESC);
CREATE INDEX idx_stay_invitations_open_expiry ON stay_invitations (expires_at) WHERE status = 'OPEN';
CREATE INDEX idx_stay_invitations_closed ON stay_invitations (status_changed_at) WHERE status <> 'OPEN';
