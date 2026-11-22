-- Migration: Baseline schema for place-service
-- Creates places, place_photos, and place_reviews tables with spatial and provider indexes

CREATE TABLE IF NOT EXISTS places (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    google_place_id VARCHAR(255) UNIQUE,
    fsq_id VARCHAR(255) UNIQUE,
    data_source VARCHAR(50) DEFAULT 'SEED_CURATED',
    name VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    address TEXT,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    rating NUMERIC(3, 2),
    user_ratings_total INTEGER DEFAULT 0,
    cover_image_url TEXT,
    detail_url TEXT,
    city_id UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS place_photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    place_id UUID NOT NULL,
    photo_url TEXT NOT NULL,
    display_order INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_place_photos_place FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS place_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    place_id UUID NOT NULL,
    author_name VARCHAR(255),
    author_avatar_url TEXT,
    rating INTEGER,
    comment TEXT,
    relative_time_description VARCHAR(100),
    publish_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_place_reviews_place FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE CASCADE
);

-- Indexes for performance and proximity search
CREATE INDEX IF NOT EXISTS idx_places_lat_lng ON places (latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_places_category ON places (category);
CREATE INDEX IF NOT EXISTS idx_places_city_id ON places (city_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_places_google_place_id ON places (google_place_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_places_fsq_id ON places (fsq_id);
CREATE INDEX IF NOT EXISTS idx_places_data_source ON places (data_source);

CREATE INDEX IF NOT EXISTS idx_place_photos_place_id ON place_photos (place_id);
CREATE INDEX IF NOT EXISTS idx_place_reviews_place_id ON place_reviews (place_id);
