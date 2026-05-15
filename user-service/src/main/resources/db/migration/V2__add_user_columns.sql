ALTER TABLE users
    ADD COLUMN is_active BOOLEAN;

ALTER TABLE users
    ADD COLUMN created_at TIMESTAMP;

UPDATE users
SET is_active = true
WHERE is_active IS NULL;

UPDATE users
SET created_at = NOW()
WHERE created_at IS NULL;

ALTER TABLE users
    ALTER COLUMN is_active SET NOT NULL;

ALTER TABLE users
    ALTER COLUMN created_at SET NOT NULL;