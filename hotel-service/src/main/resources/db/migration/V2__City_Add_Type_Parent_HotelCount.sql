-- Add type, parent, and hotel_count
ALTER TABLE cities
    ADD COLUMN IF NOT EXISTS type VARCHAR(20) NOT NULL DEFAULT 'PROVINCE',
    ADD COLUMN IF NOT EXISTS parent_id UUID NULL,
    ADD COLUMN IF NOT EXISTS hotel_count INT NOT NULL DEFAULT 0;

ALTER TABLE cities
    ADD CONSTRAINT IF NOT EXISTS fk_cities_parent
    FOREIGN KEY (parent_id) REFERENCES cities(id);

-- Set all existing to PROVINCE by default
UPDATE cities SET type = 'PROVINCE' WHERE type IS NULL;

-- Mark some special cases as CITY (trực thuộc TW)
UPDATE cities SET type = 'CITY' WHERE code IN ('DN', 'HN', 'HCM');

-- Insert city-level records under provinces
INSERT INTO cities (id, name, code, latitude, longitude, type, parent_id, hotel_count, created_at)
VALUES
    (gen_random_uuid(), 'Đà Lạt', 'DAL', 11.9404, 108.4583, 'CITY', (SELECT id FROM cities WHERE code = 'LD'), 1762, NOW()),
    (gen_random_uuid(), 'Vũng Tàu', 'VTA', 10.3460, 107.0843, 'CITY', (SELECT id FROM cities WHERE code = 'VT'), 980, NOW()),
    (gen_random_uuid(), 'Nha Trang', 'NT', 12.2388, 109.1967, 'CITY', (SELECT id FROM cities WHERE code = 'KH'), 1325, NOW());

-- Ensure hotel_count non-null
UPDATE cities SET hotel_count = COALESCE(hotel_count, 0);
