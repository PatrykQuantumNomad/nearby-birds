# Database

## Overview

The application uses **PostgreSQL 16** with the **PostGIS 3.4** spatial extension. PostGIS enables storing GPS coordinates as `geography` types and performing distance-based queries with real-world accuracy (great-circle distance in meters).

## Schema

Managed by Flyway. The single migration (`V1__create_vehicles_table.sql`) creates:

### `vehicles` Table

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGSERIAL` | PK, auto-increment | Internal row ID |
| `bird_id` | `VARCHAR(50)` | NOT NULL, UNIQUE | Human-readable vehicle identifier (e.g., `BIRD-SA-00001`) |
| `location` | `geography(Point, 4326)` | NOT NULL | GPS position stored as WGS84 geography point |
| `battery_pct` | `SMALLINT` | NOT NULL, DEFAULT 100, CHECK 0-100 | Battery percentage |
| `available` | `BOOLEAN` | NOT NULL, DEFAULT TRUE | Whether the vehicle is available for riders |
| `last_seen_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT NOW() | Last telemetry heartbeat time |
| `city` | `VARCHAR(100)` | NOT NULL | City name for grouping |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT NOW() | Record creation time |

### Indexes

| Index | Type | Purpose |
|-------|------|---------|
| `idx_vehicles_location` | GIST on `location` | Required for `ST_DWithin` to do index-accelerated radius checks instead of sequential scans |
| `idx_vehicles_available` | B-tree on `available` (partial: `WHERE available = TRUE`) | Speeds up filtering to only available vehicles |
| `idx_vehicles_city_available` | B-tree on `(city, available)` | Composite index for city-scoped availability queries |

## PostGIS Concepts

### `geography` vs `geometry`

This project uses `geography(Point, 4326)`, not `geometry`:

- **`geography`**: Coordinates are in WGS84 (SRID 4326). Distance functions return meters using great-circle math. More accurate for real-world distances but slightly slower.
- **`geometry`**: Coordinates are in a projected plane. Distance functions return units of the projection (often degrees). Faster but requires manual projection for accurate meter-based distances.

For a vehicle-search service, `geography` is the right choice because riders care about real-world meters, not angular degrees.

### SRID 4326

SRID 4326 = WGS84, the coordinate system used by GPS. Latitude ranges -90 to 90, longitude -180 to 180.

### Key PostGIS Functions Used

| Function | Usage | Description |
|----------|-------|-------------|
| `ST_MakePoint(lng, lat)` | Creating points | Note: PostGIS uses (X, Y) = (longitude, latitude) order |
| `ST_SetSRID(point, 4326)` | Setting coordinate system | Tags the point as WGS84 |
| `::geography` | Type cast | Ensures distance calculations use great-circle math (meters) |
| `ST_DWithin(geog1, geog2, distance)` | Radius filter | Returns true if two geographies are within `distance` meters. **Uses the GIST index.** |
| `ST_Distance(geog1, geog2)` | Distance calculation | Returns exact distance in meters between two geographies |
| `ST_Y(geom)` / `ST_X(geom)` | Coordinate extraction | Extracts latitude (Y) and longitude (X) from a geometry |

### The Main Query

```sql
SELECT
    v.bird_id AS "birdId",
    ST_Y(v.location::geometry) AS "latitude",
    ST_X(v.location::geometry) AS "longitude",
    ST_Distance(v.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS "distanceMeters"
FROM vehicles v
WHERE v.available = TRUE
  AND ST_DWithin(v.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radius)
ORDER BY "distanceMeters" ASC
LIMIT :limit
```

**How it works:**
1. `ST_DWithin` uses the GIST index to quickly find all points within `:radius` meters (index scan, not table scan)
2. `WHERE v.available = TRUE` further filters using the partial B-tree index
3. `ST_Distance` computes the exact distance for each matched row (for ordering)
4. Results are ordered closest-first and limited

## Flyway Migrations

- Location: `src/main/resources/db/migration/`
- Naming: `V{version}__{description}.sql` (double underscore)
- Current: `V1__create_vehicles_table.sql`
- Runs automatically on application startup
- Hibernate `ddl-auto` is set to `validate` (verifies entity mappings match the DB schema but never modifies it)

## Seed Data

`DataSeeder` inserts 500 vehicles on first startup (when the table is empty):

| City | Count | Center Lat/Lng | Spread |
|------|-------|----------------|--------|
| Santa Monica | 250 | 34.0195, -118.4912 | +/- 0.02 degrees |
| San Francisco | 250 | 37.7749, -122.4194 | +/- 0.03 degrees |

Vehicles get random battery levels (5-100%), and ~10% are marked unavailable. Bird IDs follow the pattern `BIRD-{CITY_PREFIX}-{SEQUENCE}`.

## Docker Image

Development and tests use the official `postgis/postgis:16-3.4` image. It's PostgreSQL 16 with PostGIS 3.4 pre-installed.
