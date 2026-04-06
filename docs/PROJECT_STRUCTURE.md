# Project Structure

```
nearby-birds/
├── build.gradle.kts              # Build config: plugins, dependencies, Java/Kotlin targets, bootRun password injection
├── settings.gradle.kts           # Plugin version management, project name
├── gradle.properties             # JVM args (native-access), configuration cache toggle
├── gradlew / gradlew.bat         # Gradle wrapper scripts
├── gradle/wrapper/               # Gradle wrapper JAR and properties (v8.7)
├── .java-version                 # Declares JDK 25.0.2 (for tools like jenv/asdf)
├── .gitignore                    # Ignores build/, .gradle/, .idea/, secrets/*, *.log, .env
├── Dockerfile                    # Multi-stage: JDK 25 build -> JRE 25 runtime, non-root, tini, healthcheck
├── docker-compose.yml            # PostGIS service (always) + app service (containerized profile)
├── Makefile                      # 30+ targets for Gradle, Docker, Compose, health probes
├── README.md                     # System architecture design doc + API implementation details
├── secrets/
│   ├── .gitkeep                  # Keeps the directory in git
│   └── postgres_password         # DB password (gitignored, must be created manually)
├── images/                       # README assets (demo.gif, beans.png, context.png, demo.cast)
│
├── src/main/kotlin/com/nearbybirds/
│   ├── NearbyBirdsApplication.kt           # @SpringBootApplication entry point
│   ├── config/
│   │   └── MetricsConfig.kt               # Adds common tag `application=nearby-birds` to all Micrometer meters
│   ├── controller/
│   │   ├── VehicleController.kt            # GET /api/v1/vehicles/nearby — validates params, times request, delegates to service
│   │   └── ErrorHandler.kt                 # @RestControllerAdvice — maps exceptions to ErrorResponse JSON
│   ├── service/
│   │   └── VehicleService.kt               # Business logic: calls repo, maps projections to DTOs, rounds distances, records metrics
│   ├── repository/
│   │   └── VehicleRepository.kt            # JpaRepository + native PostGIS query (ST_DWithin, ST_Distance), NearbyVehicleProjection
│   ├── model/
│   │   ├── Vehicle.kt                      # JPA @Entity mapping to `vehicles` table, geography(Point, 4326) via JTS
│   │   └── SearchResponse.kt              # Response DTOs: SearchResponse, NearbyVehicle, SearchCenter
│   ├── metrics/
│   │   └── RequestMetricsInterceptor.kt    # HandlerInterceptor: request_id MDC, per-status-code counters; WebMvcConfigurer to register it
│   └── seed/
│       └── DataSeeder.kt                   # CommandLineRunner: seeds 500 vehicles in 2 cities (skips if data exists, disabled in test)
│
├── src/main/resources/
│   ├── application.yml                     # Datasource, JPA (validate, open-in-view=false), Flyway, Actuator, logging levels
│   ├── logback-spring.xml                  # JSON logging (LogstashEncoder) in non-test, human-readable in test
│   └── db/migration/
│       └── V1__create_vehicles_table.sql   # CREATE EXTENSION postgis, CREATE TABLE vehicles, GIST index, partial index, composite index
│
└── src/test/kotlin/com/nearbybirds/
    ├── controller/
    │   └── VehicleControllerTest.kt        # @WebMvcTest: 7 tests covering 200 OK, 400 validation errors, custom params
    ├── service/
    │   └── VehicleServiceTest.kt           # Unit tests: 5 tests with mocked repository — mapping, rounding, empty results, metrics
    └── integration/
        ├── TestPostgisContainer.kt         # Abstract base: @Container PostGIS via Testcontainers, @DynamicPropertySource
        └── SearchIntegrationTest.kt        # @SpringBootTest: 11 tests against real PostGIS — radius, availability, ordering, limits, health, metrics
```

## File Count Summary

| Category | Files |
|----------|-------|
| Kotlin source (main) | 8 |
| Kotlin source (test) | 4 |
| Resources | 3 |
| SQL migrations | 1 |
| Build/config | 5 (build.gradle.kts, settings.gradle.kts, gradle.properties, .java-version, .gitignore) |
| Infrastructure | 3 (Dockerfile, docker-compose.yml, Makefile) |
| **Total tracked** | **24** |
