# Tech Stack

## Language & Runtime

| Component | Version | Notes |
|-----------|---------|-------|
| **Kotlin** | 2.3.20 | Primary language; uses data classes, extension functions, string templates |
| **Java** | 25 (JDK 25.0.2) | Target JVM; set in `.java-version` and `build.gradle.kts` (`JvmTarget.JVM_25`) |
| **Gradle** | 8.7 | Build tool with Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`) |

## Framework & Libraries

| Library | Version | Purpose |
|---------|---------|---------|
| **Spring Boot** | 3.5.13 | Application framework (web, data, actuator, validation starters) |
| **Spring Data JPA** | (managed by Boot) | Repository abstraction over Hibernate |
| **Hibernate Spatial** | 6.4.4.Final | JPA support for PostGIS geography/geometry types via JTS |
| **Jackson Kotlin Module** | (managed by Boot) | Kotlin-aware JSON serialization (camelCase, data classes) |
| **Flyway** | (managed by Boot) + `flyway-database-postgresql` | Database schema migrations |
| **Micrometer** | (managed by Boot) + `micrometer-registry-prometheus` | Metrics (counters, timers, distribution summaries) exported in Prometheus format |
| **Logstash Logback Encoder** | 7.4 | Structured JSON logging to stdout with MDC support (`request_id`, `city`) |
| **PostgreSQL JDBC** | (managed by Boot) | Database driver |
| **Kotlin Reflect** | 2.3.20 | Required by Spring for Kotlin class introspection |

## Database

| Component | Version | Notes |
|-----------|---------|-------|
| **PostgreSQL** | 16 | Relational database |
| **PostGIS** | 3.4 | Spatial extension; enables `geography(Point, 4326)`, `ST_DWithin`, `ST_Distance`, GIST indexes |
| **Docker Image** | `postgis/postgis:16-3.4` | Used in both development (docker-compose) and tests (Testcontainers) |

## Testing

| Library | Version | Purpose |
|---------|---------|---------|
| **JUnit 5** | (managed by Boot) | Test framework (`@Test`, `@BeforeEach`, lifecycle) |
| **Spring Boot Test** | (managed by Boot) | `@SpringBootTest`, `@WebMvcTest`, MockMvc |
| **Mockito Kotlin** | 5.3.1 | Kotlin-friendly mocking DSL (`mock()`, `whenever()`, `any()`) |
| **Testcontainers** | 1.19.8 | Spins up real PostGIS Docker containers for integration tests |
| **SimpleMeterRegistry** | (part of Micrometer) | In-memory meter registry for unit tests |

## Infrastructure & Build

| Tool | Purpose |
|------|---------|
| **Docker** | Container runtime for both dev database and production image |
| **Docker Compose** | Orchestrates PostGIS (always) and optionally the app container (`containerized` profile) |
| **Multi-stage Dockerfile** | Stage 1: JDK 25 Alpine builds the fat JAR. Stage 2: JRE 25 Alpine runtime with `tini` as PID 1, non-root user (UID 10001) |
| **Eclipse Temurin** | JDK/JRE base images (Alpine 3.23, digest-pinned for reproducibility) |
| **Make** | 30+ targets wrapping Gradle and Docker Compose commands (see `make help`) |
| **Docker Secrets** | `secrets/postgres_password` file mounted at `/run/secrets/` in containers; read by Gradle `bootRun` task too |

## Gradle Plugins

| Plugin | Purpose |
|--------|---------|
| `org.springframework.boot` | Fat JAR packaging, `bootRun` task |
| `io.spring.dependency-management` | BOM-based dependency version management |
| `kotlin("jvm")` | Kotlin compilation |
| `kotlin("plugin.spring")` | Opens Kotlin classes for Spring proxying (AOP, `@Configuration`, `@Transactional`) |
| `kotlin("plugin.jpa")` | Generates no-arg constructors for JPA entities |

## Gradle Configuration

- **Configuration cache** enabled (`gradle.properties`) for faster repeat builds
- **JVM args**: `--enable-native-access=ALL-UNNAMED` (required for Gradle's native-platform on JDK 24+)
- **Compiler args**: `-Xjsr305=strict` for strict nullability checking with Spring annotations
