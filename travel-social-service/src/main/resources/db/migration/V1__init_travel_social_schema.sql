CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE posts (
    id                UUID PRIMARY KEY,
    author_id         UUID        NOT NULL,
    body              TEXT        NOT NULL,
    place_id          UUID,
    rating            INTEGER,
    status            VARCHAR(16) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    status_changed_at TIMESTAMPTZ NOT NULL,
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_posts_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    CONSTRAINT ck_posts_rating_needs_place CHECK (rating IS NULL OR place_id IS NOT NULL),
    CONSTRAINT ck_posts_status CHECK (status IN ('PUBLISHED', 'HIDDEN', 'DELETED'))
);

CREATE INDEX idx_posts_feed ON posts (status, created_at DESC, id DESC);
CREATE INDEX idx_posts_place_feed ON posts (place_id, status, created_at DESC, id DESC) WHERE place_id IS NOT NULL;
CREATE INDEX idx_posts_author ON posts (author_id, created_at DESC, id DESC);
CREATE INDEX idx_posts_body_trgm ON posts USING gin (body gin_trgm_ops);
CREATE INDEX idx_posts_purge ON posts (status_changed_at) WHERE status = 'DELETED';

CREATE TABLE post_media (
    id            UUID PRIMARY KEY,
    uploader_id   UUID         NOT NULL,
    post_id       UUID REFERENCES posts (id) ON DELETE CASCADE,
    url           TEXT         NOT NULL,
    object_name   TEXT         NOT NULL,
    mime_type     VARCHAR(64)  NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    display_order INTEGER      NOT NULL DEFAULT 0,
    status        VARCHAR(16)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_post_media_status CHECK (status IN ('PENDING', 'ATTACHED')),
    CONSTRAINT ck_post_media_attachment CHECK ((status = 'ATTACHED') = (post_id IS NOT NULL))
);

CREATE INDEX idx_post_media_post ON post_media (post_id, display_order) WHERE post_id IS NOT NULL;
CREATE INDEX idx_post_media_pending ON post_media (created_at) WHERE status = 'PENDING';
CREATE INDEX idx_post_media_uploader_pending ON post_media (uploader_id) WHERE status = 'PENDING';

CREATE TABLE post_likes (
    post_id    UUID        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_post_likes PRIMARY KEY (post_id, user_id)
);

CREATE TABLE comments (
    id                UUID PRIMARY KEY,
    post_id           UUID        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    author_id         UUID        NOT NULL,
    body              TEXT        NOT NULL,
    status            VARCHAR(16) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    status_changed_at TIMESTAMPTZ NOT NULL,
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_comments_status CHECK (status IN ('PUBLISHED', 'HIDDEN', 'DELETED'))
);

CREATE INDEX idx_comments_post ON comments (post_id, status, created_at, id);
CREATE INDEX idx_comments_purge ON comments (status_changed_at) WHERE status = 'DELETED';

CREATE TABLE reports (
    id          UUID PRIMARY KEY,
    reporter_id UUID        NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id   UUID        NOT NULL,
    reason      VARCHAR(32) NOT NULL,
    note        TEXT,
    status      VARCHAR(16) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    closed_at   TIMESTAMPTZ,
    closed_by   UUID,
    CONSTRAINT uq_reports_reporter_target UNIQUE (reporter_id, target_type, target_id),
    CONSTRAINT ck_reports_target_type CHECK (target_type IN ('POST', 'COMMENT')),
    CONSTRAINT ck_reports_reason CHECK (reason IN ('SPAM', 'HARASSMENT', 'INAPPROPRIATE_MEDIA', 'MISLEADING', 'OTHER')),
    CONSTRAINT ck_reports_status CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED'))
);

CREATE INDEX idx_reports_queue ON reports (status, created_at);
CREATE INDEX idx_reports_target ON reports (target_type, target_id);

CREATE TABLE moderation_audit (
    id          UUID PRIMARY KEY,
    actor_id    UUID        NOT NULL,
    action      VARCHAR(32) NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id   UUID        NOT NULL,
    reason      TEXT,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_moderation_audit_target ON moderation_audit (target_type, target_id, created_at);
