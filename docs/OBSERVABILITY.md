# Observability

## Metrics (Micrometer + Prometheus)

The app uses Micrometer with the Prometheus registry. All metrics are exposed at `GET /actuator/prometheus`.

### Common Tags

Every metric carries `application=nearby-birds` (configured in `MetricsConfig`).

### Custom Metrics

| Metric Name | Type | Tags | Where Recorded | Description |
|-------------|------|------|----------------|-------------|
| `nearby.search.requests` | Counter | `status` = `ok` or `error` | `VehicleController` (ok), `RequestMetricsInterceptor` (error) | Total search requests by outcome |
| `nearby.search.latency` | Timer | `endpoint` = `nearby` | `VehicleController` | End-to-end request duration histogram |
| `nearby.search.results.count` | Distribution Summary | - | `VehicleService` | Distribution of how many vehicles each search returns |
| `http.server.requests.status` | Counter | `status` = HTTP code (200, 400, 500) | `RequestMetricsInterceptor` | Per-HTTP-status request counts |

### Auto-Configured Metrics

Spring Boot Actuator auto-registers standard JVM, Tomcat, and Hikari metrics:
- `jvm_memory_*`, `jvm_gc_*`, `jvm_threads_*`
- `tomcat_*` (connections, threads, request processing)
- `hikaricp_*` (connection pool stats)
- `process_*` (CPU, uptime)

### Querying in Prometheus

```promql
# Request rate
rate(nearby_search_requests_total{status="ok"}[5m])

# P99 latency
histogram_quantile(0.99, rate(nearby_search_latency_seconds_bucket[5m]))

# Average result count
rate(nearby_search_results_count_sum[5m]) / rate(nearby_search_results_count_count[5m])
```

## Structured Logging

### JSON Format (non-test)

In production and development, logs are emitted as structured JSON via the Logstash Logback encoder (`logback-spring.xml`):

```json
{
  "@timestamp": "2024-01-15T10:30:00.000Z",
  "level": "INFO",
  "logger_name": "com.nearbybirds.service.VehicleService",
  "message": "Search completed: lat=34.0195, lng=-118.4912, radius=500.0m, results=12",
  "request_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "thread_name": "http-nio-8080-exec-1"
}
```

### MDC Fields

The `RequestMetricsInterceptor` adds these to the SLF4J MDC:

| MDC Key | Source | Description |
|---------|--------|-------------|
| `request_id` | `X-Request-Id` header, or generated UUID | Unique request correlation ID |

These are automatically included in every log line during request processing and cleared in `afterCompletion`.

### Human-Readable Format (test profile)

When `spring.profiles.active=test`, logs use a simple pattern:

```
10:30:00.123 [main] INFO  c.n.service.VehicleService - Search completed: lat=34.0195, lng=-118.4912, radius=500.0m, results=12
```

### Log Levels

Configured in `application.yml`:

| Logger | Level |
|--------|-------|
| `com.nearbybirds` | DEBUG |
| `org.hibernate.SQL` | WARN |
| Root | INFO (Logback default) |

## Health Checks (Spring Actuator)

Three health endpoints are exposed:

| Endpoint | Purpose | Checks |
|----------|---------|--------|
| `GET /actuator/health` | Combined | All health indicators with full details |
| `GET /actuator/health/liveness` | K8s liveness probe | Process is alive (always UP if responding) |
| `GET /actuator/health/readiness` | K8s readiness probe | Database is reachable |

The Dockerfile includes a `HEALTHCHECK` that polls the readiness endpoint:

```dockerfile
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness | grep -q UP
```

Docker Compose mirrors this for the `app` service.

### Actuator Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,info   # Only these three are exposed
  endpoint:
    health:
      show-details: always                # Full details in health response
      probes:
        enabled: true                     # Enable /liveness and /readiness sub-paths
```

## Recommended Alerting (from README design)

| Condition | Severity |
|-----------|----------|
| P99 latency > 100ms | Page |
| Ghost filter rate > 30% in a city | Warning |
| Redis connection failures | Readiness probe fails, traffic shifts |
