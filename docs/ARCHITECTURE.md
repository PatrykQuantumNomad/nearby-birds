# Architecture

## System Overview

Nearby Birds is the **Read Path Search Service** of a larger vehicle-sharing platform. It answers one question: given a rider's GPS coordinates, which Bird vehicles are nearby and available?

The broader system (described in the README but not all implemented in this repo) separates **write** and **read** paths:

```
Write Path (not in this repo):
  Vehicle Fleet -> API Gateway -> Ingestion Service -> Kafka -> Flink/KStreams -> Redis + PostGIS

Read Path (THIS REPO):
  Rider App -> API Gateway -> Search Service -> PostGIS
```

This repository implements only the **Search Service** backed by PostGIS. The Redis hot-cache layer and the write-path ingestion pipeline are described architecturally but not implemented here.

## Application Architecture

The app follows a classic **Spring Boot layered architecture**:

```
HTTP Request
    |
    v
VehicleController          <-- REST layer: input validation, metrics, HTTP response shaping
    |
    v
VehicleService             <-- Business logic: coordinate mapping, distance rounding, observability
    |
    v
VehicleRepository          <-- Data access: native PostGIS SQL via Spring Data JPA
    |
    v
PostgreSQL + PostGIS       <-- Geospatial database with GIST-indexed geography column
```

### Layer Responsibilities

**Controller (`VehicleController`)**
- Defines the `GET /api/v1/vehicles/nearby` endpoint
- Uses Jakarta Bean Validation annotations for input constraints (lat/lng ranges, radius bounds, limit bounds)
- Records request latency via a Micrometer `Timer` and increments a request counter
- Delegates to `VehicleService` and wraps the result in `ResponseEntity`

**Error Handling (`ErrorHandler`)**
- A `@RestControllerAdvice` that catches validation, missing-parameter, type-mismatch, and generic exceptions
- Returns consistent `ErrorResponse` JSON bodies with `error`, `message`, and `status` fields
- Logs unexpected errors at ERROR level

**Service (`VehicleService`)**
- Calls the repository and maps `NearbyVehicleProjection` results into `NearbyVehicle` DTOs
- Rounds distances to 1 decimal place
- Records result-set size as a Micrometer distribution summary
- Logs search parameters and result counts

**Repository (`VehicleRepository`)**
- Extends `JpaRepository<Vehicle, Long>`
- Contains a single native SQL query using PostGIS functions:
  - `ST_DWithin` for index-accelerated radius filtering on the `geography` column
  - `ST_Distance` for computing exact distances for ordering
  - `ST_Y`/`ST_X` for extracting lat/lng from the stored point
- Uses a `NearbyVehicleProjection` interface to avoid loading full JPA entities
- Filters to `available = TRUE` vehicles only

**Model**
- `Vehicle` (JPA entity) maps to the `vehicles` table with a `geography(Point, 4326)` column via JTS `Point`
- `SearchResponse`, `NearbyVehicle`, `SearchCenter` are plain data classes for the API response

**Data Seeder (`DataSeeder`)**
- A `CommandLineRunner` that inserts 500 sample vehicles (250 in Santa Monica, 250 in San Francisco) on startup
- Only runs when the table is empty and the `test` profile is NOT active
- Uses raw JDBC for inserts (not JPA) for simplicity

**Metrics & Interceptor**
- `MetricsConfig` adds `application=nearby-birds` as a common tag to all meters
- `RequestMetricsInterceptor` is a Spring MVC `HandlerInterceptor` that:
  - Puts a `request_id` (from `X-Request-Id` header or UUID) into the SLF4J MDC for structured logging
  - Counts requests by HTTP status code
  - Counts error requests separately

## Request Flow (detailed)

1. HTTP `GET /api/v1/vehicles/nearby?lat=34.0&lng=-118.5&radius=500&limit=20` arrives
2. `RequestMetricsInterceptor.preHandle` sets `request_id` in MDC and records start time
3. Spring validates params via Bean Validation annotations on `VehicleController.searchNearby`
4. If validation fails, `ErrorHandler` catches the exception and returns 400 with `ErrorResponse`
5. Controller starts a Micrometer timer, increments `nearby.search.requests{status=ok}`
6. `VehicleService.searchNearby` calls `VehicleRepository.findNearbyAvailableVehicles`
7. Repository executes native PostGIS query (ST_DWithin + ST_Distance) against the GIST index
8. Service maps projections to DTOs, rounds distances, records `nearby.search.results.count`
9. Controller wraps result in `ResponseEntity<SearchResponse>` and returns 200
10. `RequestMetricsInterceptor.afterCompletion` counts the status code, clears MDC

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Native SQL over HQL/JPQL | PostGIS spatial functions (`ST_DWithin`, `ST_Distance`) require native queries. A projection interface avoids mapping overhead. |
| `geography` type over `geometry` | `geography` uses WGS84 great-circle distance (meters), making radius queries physically accurate without projection math. |
| GIST spatial index | Required for `ST_DWithin` to use index-accelerated distance checks instead of sequential scan. |
| Partial index on `available` | `WHERE available = TRUE` makes the common-case filter effectively free. |
| Validation at controller | Jakarta Bean Validation + `@Validated` catches bad input before hitting the service/DB layer. |
| `ddl-auto: validate` | Flyway manages the schema; Hibernate only verifies it matches entity mappings. No accidental DDL. |
| `open-in-view: false` | Prevents lazy-loading surprises outside of transactions. All data is fetched eagerly in the repository query. |
| DataSeeder skips in `test` profile | Tests manage their own data via `TestPostgisContainer` and direct JDBC inserts. |
