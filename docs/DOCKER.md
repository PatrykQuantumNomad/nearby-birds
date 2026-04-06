# Docker & Deployment

## Docker Image (Dockerfile)

The Dockerfile uses a **multi-stage build** optimized for production:

### Stage 1: Build

```
Base: eclipse-temurin:25-jdk-alpine-3.23 (digest-pinned)
```

1. Copies Gradle wrapper and build files first (for dependency layer caching)
2. Runs `./gradlew dependencies` to download dependencies (cached layer)
3. Copies source code
4. Runs `./gradlew bootJar -x test` to build the fat JAR

### Stage 2: Runtime

```
Base: eclipse-temurin:25-jre-alpine-3.23 (digest-pinned)
```

1. Installs `tini` (lightweight init system, PID 1) for proper signal handling
2. Creates a non-root user `app` (UID/GID 10001)
3. Copies the fat JAR from the build stage
4. Runs as non-root user
5. Exposes port 8080
6. Health check polls `/actuator/health/readiness` via `wget`
7. Entry: `tini -- java -jar app.jar`

### Security Hardening

| Feature | Detail |
|---------|--------|
| Non-root user | UID 10001, no login shell |
| Digest-pinned base images | Prevents supply chain drift |
| Alpine minimal base | Small attack surface |
| `tini` as PID 1 | Proper zombie reaping and signal forwarding |
| Read-only filesystem | Set in docker-compose.yml |
| `no-new-privileges` | Prevents privilege escalation |
| `cap_drop: ALL` | Drops all Linux capabilities |

### Building

```bash
make docker-build                              # Default tag: nearby-birds:local
make docker-build IMAGE_NAME=my/birds:v1       # Custom tag
make docker-build DOCKER_PLATFORM=linux/amd64  # Cross-platform build
make docker-build-nc                           # No cache
```

## Docker Compose

### Services

**`postgres`** (always started):
- Image: `postgis/postgis:16-3.4`
- Port: `127.0.0.1:5432:5432` (localhost only)
- Password from Docker secret (`secrets/postgres_password`)
- Health check: `pg_isready -U birds -d nearby_birds` (every 5s)
- Resource limits: 2 CPU, 1GB RAM
- Read-only filesystem with tmpfs for `/tmp` and `/var/run/postgresql`
- Persistent volume `pgdata` for data directory

**`app`** (only with `--profile containerized`):
- Built from the local Dockerfile
- Port: `127.0.0.1:8080:8080` (localhost only)
- Depends on `postgres` being healthy
- Reads DB password from Docker secret at runtime via shell expansion
- Health check: `/actuator/health/readiness` (every 30s, 90s start period)
- Resource limits: 1 CPU, 1GB RAM
- Read-only filesystem with tmpfs for `/tmp` and `/var/tmp`

### Network

Single `backend` bridge network. No services are exposed to the host network beyond the explicitly mapped ports.

### Why Profiles?

The `containerized` profile on the `app` service means:
- `docker compose up` only starts PostGIS (for local JVM development)
- `docker compose --profile containerized up --build` starts both (for containerized mode)

This avoids accidentally building the app image when you just need the database.

### Common Commands

```bash
make dev-db         # PostGIS only
make up-build       # Both services (builds app image)
make down           # Stop, keep volumes
make down-volumes   # Stop, wipe DB data
make logs           # Follow all logs
make logs-db        # Follow PostGIS logs only
make ps             # Service status
```
