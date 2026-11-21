-- The unattached-photo cleanup window starts when a photo becomes unattached (upload, or removal from
-- a post during an edit), not when it was uploaded.
ALTER TABLE post_media ADD COLUMN pending_since TIMESTAMPTZ;
UPDATE post_media SET pending_since = created_at WHERE status = 'PENDING';
ALTER TABLE post_media
    ADD CONSTRAINT ck_post_media_pending_since CHECK ((status = 'PENDING') = (pending_since IS NOT NULL));

DROP INDEX idx_post_media_pending;
CREATE INDEX idx_post_media_pending ON post_media (pending_since) WHERE status = 'PENDING';
