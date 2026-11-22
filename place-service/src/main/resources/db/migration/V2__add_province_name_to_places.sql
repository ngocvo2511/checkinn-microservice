-- Migration: Add province_name to places table
-- Enables standalone geographic querying and eliminates foreign-database dependency

ALTER TABLE places 
    ADD COLUMN IF NOT EXISTS province_name VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_places_province_name ON places (province_name);

COMMENT ON COLUMN places.province_name IS 'Name of the province/city (e.g. Hà Nội, Đà Nẵng, Lâm Đồng) for standalone querying';
