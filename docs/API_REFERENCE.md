# API Reference

Base URL: `http://localhost:8080`

## Endpoints

### Search Nearby Vehicles

```
GET /api/v1/vehicles/nearby
```

Find available Bird vehicles within a given radius of a GPS coordinate.

**Query Parameters:**

| Parameter | Type   | Required | Default | Constraints      | Description                |
|-----------|--------|----------|---------|------------------|----------------------------|
| `lat`     | double | yes      | -       | -90.0 to 90.0    | Latitude of search center  |
| `lng`     | double | yes      | -       | -180.0 to 180.0  | Longitude of search center |
| `radius`  | double | no       | 500.0   | 1.0 to 5000.0    | Search radius in meters    |
| `limit`   | int    | no       | 20      | 1 to 100         | Maximum number of results  |

**Success Response (200 OK):**

```json
{
  "vehicles": [
    {
      "birdId": "BIRD-00042",
      "latitude": 34.0522,
      "longitude": -118.2437,
      "distanceMeters": 142.7
    }
  ],
  "count": 1,
  "searchCenter": {
    "latitude": 34.0525,
    "longitude": -118.2430
  },
  "searchRadiusMeters": 500.0
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `vehicles` | array | List of nearby available vehicles, ordered by distance ascending |
| `vehicles[].birdId` | string | Unique vehicle identifier |
| `vehicles[].latitude` | double | Vehicle latitude |
| `vehicles[].longitude` | double | Vehicle longitude |
| `vehicles[].distanceMeters` | double | Distance from search center in meters (rounded to 1 decimal) |
| `count` | int | Number of vehicles returned |
| `searchCenter.latitude` | double | Echo of the requested latitude |
| `searchCenter.longitude` | double | Echo of the requested longitude |
| `searchRadiusMeters` | double | Echo of the search radius used |

**Error Response (400 Bad Request):**

```json
{
  "error": "validation_error",
  "message": "must be less than or equal to 90.0",
  "status": 400
}
```

**Error Types:**

| `error` value | Trigger |
|---------------|---------|
| `validation_error` | Parameter out of allowed range |
| `missing_parameter` | Required parameter (`lat` or `lng`) not provided |
| `invalid_parameter` | Parameter is not the expected type (e.g., `lat=abc`) |
| `internal_error` | Unexpected server error |

---

### Health Endpoints (Spring Actuator)

**Combined Health:**
```
GET /actuator/health
```
Returns overall status plus details for each indicator (db, liveness, readiness).

**Liveness Probe:**
```
GET /actuator/health/liveness
```
Returns `200 {"status":"UP"}` if the process is alive. Used by Kubernetes liveness probe.

**Readiness Probe:**
```
GET /actuator/health/readiness
```
Returns `200 {"status":"UP"}` if the database is reachable. Used by Kubernetes readiness probe.

---

### Prometheus Metrics

```
GET /actuator/prometheus
```

Exposes all Micrometer metrics in Prometheus text exposition format.

**Key custom metrics:**

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `nearby_search_requests_total` | counter | `status` (ok/error) | Total search requests |
| `nearby_search_latency_seconds` | timer | `endpoint` | Request latency histogram |
| `nearby_search_results_count` | distribution summary | - | Distribution of result set sizes |
| `http_server_requests_status_total` | counter | `status` (200/400/500) | Per-HTTP-status request counts |

All metrics carry the common tag `application=nearby-birds`.

---

## Example curl Commands

```bash
# Basic search (defaults: radius=500m, limit=20)
curl -sS 'http://127.0.0.1:8080/api/v1/vehicles/nearby?lat=34.0195&lng=-118.4912'

# Custom radius and limit
curl -sS 'http://127.0.0.1:8080/api/v1/vehicles/nearby?lat=34.0195&lng=-118.4912&radius=1000&limit=50'

# San Francisco area
curl -sS 'http://127.0.0.1:8080/api/v1/vehicles/nearby?lat=37.7749&lng=-122.4194&radius=800'

# Pretty print with jq
curl -sS 'http://127.0.0.1:8080/api/v1/vehicles/nearby?lat=34.0195&lng=-118.4912' | jq .

# Health check
curl -sS 'http://127.0.0.1:8080/actuator/health'

# Prometheus metrics (first 40 lines)
curl -sS 'http://127.0.0.1:8080/actuator/prometheus' | head -n 40
```
