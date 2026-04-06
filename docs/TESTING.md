# Testing

## Overview

The project has three test layers:

| Layer | Location | Framework | DB | Count |
|-------|----------|-----------|-----|-------|
| Unit (service) | `service/VehicleServiceTest` | JUnit 5 + Mockito Kotlin | Mocked | 5 tests |
| Unit (controller) | `controller/VehicleControllerTest` | `@WebMvcTest` + MockMvc | No DB | 7 tests |
| Integration | `integration/SearchIntegrationTest` | `@SpringBootTest` + Testcontainers | Real PostGIS | 11 tests |

**Total: 23 tests**

## Running Tests

```bash
# All tests (requires Docker for Testcontainers)
make test
# or
./gradlew test
```

Docker must be running because integration tests spin up a real PostGIS container via Testcontainers.

## Unit Tests: VehicleServiceTest

Tests the service layer in isolation with a mocked repository.

| Test | What It Verifies |
|------|------------------|
| `searchNearby returns mapped vehicles from repository` | Projection-to-DTO mapping (birdId, lat, lng, distance) |
| `searchNearby returns empty list when no vehicles found` | Empty result handling |
| `searchNearby populates search center from input coordinates` | SearchCenter and radius echoed correctly |
| `searchNearby rounds distance to one decimal place` | 142.7567 -> 142.8 |
| `searchNearby records result count metric` | Micrometer summary records result set size |

**Key patterns:**
- Uses `SimpleMeterRegistry` for metric verification
- Creates anonymous `NearbyVehicleProjection` implementations for mock data
- No Spring context loaded — pure unit test

## Unit Tests: VehicleControllerTest

Tests the REST layer via `@WebMvcTest` (loads only the web slice, no DB).

| Test | What It Verifies |
|------|------------------|
| `GET nearby returns 200 with vehicle list` | Happy path: correct JSON structure |
| `GET nearby with custom radius and limit` | Non-default params accepted |
| `GET nearby without required lat returns 400` | Missing required param |
| `GET nearby without required lng returns 400` | Missing required param |
| `GET nearby with lat out of range returns 400` | Validation: lat > 90 |
| `GET nearby with lng out of range returns 400` | Validation: lng < -180 |
| `GET nearby with non-numeric lat returns 400` | Type mismatch handling |

**Key patterns:**
- `@MockBean` for `VehicleService`
- `TestMetricsConfig` provides a `SimpleMeterRegistry` bean (avoids needing Prometheus on classpath)
- Uses MockMvc Kotlin DSL (`mockMvc.get { ... }.andExpect { ... }`)

## Integration Tests: SearchIntegrationTest

Full-stack tests against a real PostGIS database via Testcontainers.

### Test Infrastructure

`TestPostgisContainer` is an abstract base class that:
1. Starts a `postgis/postgis:16-3.4` container (shared across all tests via `companion object`)
2. Uses `@DynamicPropertySource` to inject the container's JDBC URL, username, and password into the Spring context
3. Flyway runs the real migration against this container

### Test Data Setup

Each test gets a clean slate:
- `@BeforeEach` deletes all vehicles and re-inserts 5 known test vehicles around the Santa Monica Pier
- Vehicles are at known distances with different availability states and battery levels

**Test Vehicles:**

| Bird ID | Distance from Pier | Available | Battery |
|---------|-------------------|-----------|---------|
| BIRD-001 | ~100m | yes | 80% |
| BIRD-002 | ~200m | yes | 60% |
| BIRD-003 | ~50m | **no** | 90% |
| BIRD-004 | ~5km | yes | 70% |
| BIRD-005 | ~300m | yes | 8% |

### Test Cases

| Test | What It Verifies |
|------|------------------|
| `search nearby returns available vehicles within radius` | 3 available vehicles within 500m (excludes BIRD-003 unavailable, BIRD-004 too far) |
| `search nearby excludes unavailable vehicles` | BIRD-003 never appears even within 5km radius |
| `search nearby returns results ordered by distance ascending` | Closest vehicle (BIRD-001) is first |
| `search with small radius returns only very close vehicles` | 120m radius returns only BIRD-001 |
| `search with limit restricts result count` | `limit=2` returns exactly 2 |
| `search at location with no vehicles returns empty list` | (0, 0) returns empty |
| `search with missing lat returns 400` | Validation at HTTP level |
| `search with invalid lat returns 400` | `lat=91.0` rejected |
| `health liveness endpoint returns 200` | Actuator liveness probe works |
| `health readiness endpoint returns 200` | Actuator readiness probe works (DB is up) |
| `prometheus metrics endpoint returns 200` | Metrics endpoint contains `nearby_search_latency` |

### Profile

Integration tests use `@ActiveProfiles("test")` which:
- Switches logback to human-readable format
- Disables `DataSeeder` (it's `@Profile("!test")`)

## Test Architecture Diagram

```
VehicleControllerTest
  └── @WebMvcTest (web slice only)
       ├── MockMvc (HTTP simulation)
       ├── @MockBean VehicleService
       └── SimpleMeterRegistry

VehicleServiceTest
  └── Plain JUnit (no Spring)
       ├── mock<VehicleRepository>()
       └── SimpleMeterRegistry

SearchIntegrationTest
  └── @SpringBootTest (full context)
       ├── Testcontainers PostGIS
       ├── Real Flyway migration
       ├── Real JPA + Hibernate Spatial
       └── MockMvc (HTTP simulation)
```
