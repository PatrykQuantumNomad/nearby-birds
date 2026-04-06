# Development Guide

## Prerequisites

- **JDK 25+** (project uses `.java-version` file for tool managers like jenv/asdf)
- **Docker & Docker Compose v2** (for PostGIS and optionally running the app)
- **Make** (optional but recommended; wraps all common commands)

## First-Time Setup

1. **Create the database password file:**

   ```bash
   mkdir -p secrets
   printf "%s" "your-password" > secrets/postgres_password
   chmod 600 secrets/postgres_password
   ```

   This file is gitignored. Both Docker Compose and the Gradle `bootRun` task read it.

2. **Start PostGIS:**

   ```bash
   make dev-db
   # or: docker compose up -d postgres
   ```

3. **Run the application:**

   ```bash
   make run
   # or: ./gradlew bootRun
   ```

   The app starts on `http://localhost:8080`. On first run, Flyway applies the migration and `DataSeeder` inserts 500 sample vehicles.

4. **Verify it works:**

   ```bash
   make health
   curl -sS 'http://127.0.0.1:8080/api/v1/vehicles/nearby?lat=34.0195&lng=-118.4912' | jq .
   ```

## Development Workflows

### Option A: Local JVM (recommended for development)

Only PostGIS runs in Docker. The app runs on the host via Gradle, enabling fast recompile and restart.

```bash
make dev-db    # Start PostGIS
make run       # Start Spring Boot (reads secrets/postgres_password)
```

### Option B: Fully Containerized

Both the app and PostGIS run in Docker. Useful when you don't want Gradle/JDK on the host.

```bash
make up-build  # Builds the app Docker image and starts both services
```

The app service is gated behind the `containerized` Docker Compose profile, so a plain `docker compose up` only starts PostGIS.

## Key Make Targets

Run `make help` for the full list. Highlights:

| Target | Description |
|--------|-------------|
| `make run` | Start Spring Boot on host (Gradle bootRun) |
| `make dev-db` | Start only PostGIS in Docker |
| `make up-build` | Start API + PostGIS in Docker (builds image) |
| `make test` | Run unit + integration tests |
| `make check` | Run tests then build the JAR (CI gate) |
| `make build` | Build the fat JAR only |
| `make down` | Stop containers, keep volumes |
| `make down-volumes` | Stop containers and wipe DB data |
| `make health` | Hit `/actuator/health` |
| `make readiness` | Hit `/actuator/health/readiness` |
| `make logs` | Follow all Compose service logs |
| `make clean-all` | Full cleanup: containers, volumes, Gradle build, Docker image |

## Configuration

Configuration is in `src/main/resources/application.yml`. Key settings:

| Property | Default | Override via |
|----------|---------|-------------|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/nearby_birds` | `SPRING_DATASOURCE_URL` env var |
| `spring.datasource.password` | `birds_secret` | `SPRING_DATASOURCE_PASSWORD` env var or `secrets/postgres_password` file |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Schema is managed by Flyway, not Hibernate |
| `server.port` | `8080` | `SERVER_PORT` env var |

## How the Password Flows

1. **Docker Compose**: reads `secrets/postgres_password` as a Docker secret, mounted at `/run/secrets/postgres_password` in containers
2. **`make run` / `bootRun`**: the `build.gradle.kts` `BootRun` task reads `secrets/postgres_password` and sets `SPRING_DATASOURCE_PASSWORD` if not already in the environment
3. **`application.yml`**: uses `${SPRING_DATASOURCE_PASSWORD:birds_secret}` (fallback for when no env var is set)

## Rebuilding

```bash
make clean-gradle   # Wipe Gradle build output
make build          # Rebuild the fat JAR
make docker-build   # Rebuild the Docker image (tagged nearby-birds:local)
```

## Connecting to the Database Directly

```bash
docker exec -it nearby-birds-db psql -U birds -d nearby_birds
```

Useful queries:
```sql
SELECT COUNT(*) FROM vehicles;
SELECT COUNT(*) FROM vehicles WHERE available = TRUE;
SELECT bird_id, ST_AsText(location::geometry), battery_pct, available FROM vehicles LIMIT 5;
```
