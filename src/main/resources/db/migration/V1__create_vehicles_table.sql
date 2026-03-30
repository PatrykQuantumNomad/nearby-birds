-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE vehicles (
    id            BIGSERIAL PRIMARY KEY,
    bird_id       VARCHAR(50)  NOT NULL UNIQUE,
    location      geography(Point, 4326) NOT NULL,
    battery_pct   SMALLINT     NOT NULL DEFAULT 100 CHECK (battery_pct BETWEEN 0 AND 100),
    available     BOOLEAN      NOT NULL DEFAULT TRUE,
    last_seen_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    city          VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Spatial index on location for efficient radius queries
CREATE INDEX idx_vehicles_location ON vehicles USING GIST (location);

-- B-tree index on availability for filtering
CREATE INDEX idx_vehicles_available ON vehicles (available) WHERE available = TRUE;

-- Composite index for the most common query pattern: available vehicles within a radius
CREATE INDEX idx_vehicles_city_available ON vehicles (city, available);
