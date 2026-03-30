# =============================================================================
# nearby-birds — Makefile
# =============================================================================
# Spring Boot (Kotlin) API backed by PostGIS. This file documents the common
# workflows for local development, testing, OCI images, and Docker Compose.
#
# Prerequisites:
#   - JDK 25+ on PATH (for Gradle)
#   - Docker Engine + Docker Compose v2 (`docker compose`)
#
# First-time Compose setup:
#   Create secrets/postgres_password with a single line (the DB password).
#   It is gitignored. See `make secrets-help`. `make run` reads the same file.
#
# Typical flows:
#   make help              # list targets
#   make dev-db   / make up   # PostGIS only → then `make run` on the host
#   make up-build          # API + PostGIS in Docker (--profile containerized)
#   make test              # unit + integration (Docker required for Testcontainers)
#
# Override defaults on the command line, e.g.
#   make docker-build DOCKER_PLATFORM=linux/amd64 IMAGE_NAME=my/nearby-birds:dev
#
# Variables (optional):
#   IMAGE_NAME      — tag for `make docker-build` (default: nearby-birds:local)
#   APP_PORT        — for `make health` / `make readiness` (default: 8080)
#   DOCKER_PLATFORM — e.g. linux/amd64 when building on Apple Silicon for Linux servers
# =============================================================================

SHELL := /bin/sh
.DEFAULT_GOAL := help

# macOS: if JAVA_HOME is empty, use the default JDK from the OS (avoids broken jenv shims on PATH).
ifeq ($(strip $(JAVA_HOME)),)
  UNAME_S := $(shell uname -s 2>/dev/null || true)
  ifeq ($(UNAME_S),Darwin)
    _JAVA_HOME := $(shell /usr/libexec/java_home 2>/dev/null || true)
    ifneq ($(_JAVA_HOME),)
      export JAVA_HOME := $(_JAVA_HOME)
    endif
  endif
endif

# --- configurable paths & names ------------------------------------------------
ROOT_DIR        := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
COMPOSE_FILE    ?= $(ROOT_DIR)/docker-compose.yml
GRADLEW         := $(ROOT_DIR)/gradlew
DOCKER          ?= docker
COMPOSE                 ?= docker compose --project-directory $(ROOT_DIR) -f $(COMPOSE_FILE)
# Enables the Spring Boot service in docker-compose.yml (Postgres has no profile).
COMPOSE_PROFILE_STACK ?= containerized
IMAGE_NAME      ?= nearby-birds:local
SECRET_FILE     := $(ROOT_DIR)/secrets/postgres_password
APP_PORT        ?= 8080

# Optional: set DOCKER_PLATFORM=linux/amd64 (or arm64) when building for another arch.
DOCKER_PLATFORM ?=
DOCKER_BUILD    := $(DOCKER) build
ifneq ($(strip $(DOCKER_PLATFORM)),)
  DOCKER_BUILD += --platform $(DOCKER_PLATFORM)
endif

# BuildKit is recommended for the Dockerfile (syntax directive, multi-stage).
export DOCKER_BUILDKIT ?= 1

# =============================================================================
# Help — default target. One target name per rule so `##` lines parse correctly.
# =============================================================================

.PHONY: help
help: ## Show this help (all documented targets)
	@printf '%s\n' "nearby-birds — available targets:"
	@awk 'BEGIN {FS = ":.*## "}; /^[a-zA-Z0-9_.-]+:.*## / {printf "  %-24s %s\n", $$1, $$2}' $(MAKEFILE_LIST) | sort -u

# =============================================================================
# Application (JVM / Gradle) — run on the host against a reachable Postgres.
# =============================================================================

.PHONY: run
run: secrets-check ## Start Spring Boot on host (Postgres password from secrets/postgres_password)
	cd $(ROOT_DIR) && SPRING_DATASOURCE_PASSWORD="$$(tr -d '\n\r' < '$(SECRET_FILE)')" $(GRADLEW) bootRun

.PHONY: boot-run
boot-run: run ## Same as `make run`

.PHONY: build
build: ## Build the fat JAR (build/libs/*.jar)
	cd $(ROOT_DIR) && $(GRADLEW) bootJar

.PHONY: jar boot-jar
jar: build ## Alias for `make build`
boot-jar: build ## Alias for `make build`

.PHONY: test
test: ## Run unit + integration tests (Docker required for Testcontainers)
	cd $(ROOT_DIR) && $(GRADLEW) test

.PHONY: check
check: test build ## Run tests then build the JAR (CI-style local gate)

.PHONY: clean-gradle
clean-gradle: ## Run ./gradlew clean
	cd $(ROOT_DIR) && $(GRADLEW) clean

# =============================================================================
# Secrets (Compose database password file)
# =============================================================================

.PHONY: secrets-help
secrets-help: ## Print how to create secrets/postgres_password for Compose
	@printf '%s\n' \
	  "Compose expects a Docker secret file at:" \
	  "  $(SECRET_FILE)" \
	  "" \
	  "Create it once (use your own strong password, single line, no trailing newline):" \
	  "  mkdir -p $(ROOT_DIR)/secrets" \
	  '  printf "%s" "your-password" > $(SECRET_FILE)' \
	  "  chmod 600 $(SECRET_FILE)"

.PHONY: secrets-check
secrets-check: ## Fail fast if secrets/postgres_password is missing (Compose)
	@test -f $(SECRET_FILE) || (printf '%s\n' "Missing $(SECRET_FILE)" "Run: make secrets-help" >&2; exit 1)

# =============================================================================
# Docker image (OCI) — optional build-args: see Dockerfile ARG … comments
# =============================================================================

.PHONY: docker-build
docker-build: ## Build OCI image (default tag: nearby-birds:local; override IMAGE_NAME=)
	$(DOCKER_BUILD) -t $(IMAGE_NAME) -f $(ROOT_DIR)/Dockerfile $(ROOT_DIR)

.PHONY: docker-build-nc
docker-build-nc: ## Build OCI image with --no-cache
	$(DOCKER_BUILD) --no-cache -t $(IMAGE_NAME) -f $(ROOT_DIR)/Dockerfile $(ROOT_DIR)

.PHONY: docker-images
docker-images: ## List nearby-birds-related image rows
	@$(DOCKER) images --format 'table {{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.Size}}' | head -1
	@$(DOCKER) images --format 'table {{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.Size}}' | grep -E 'nearby-birds|REPOSITORY' || true

.PHONY: docker-rmi-local
docker-rmi-local: ## Remove IMAGE_NAME from local engine (ignore if missing)
	@$(DOCKER) rmi $(IMAGE_NAME) 2>/dev/null || true

# =============================================================================
# Docker Compose — project name comes from compose file: name: nearby-birds
# =============================================================================

.PHONY: compose-config
compose-config: ## Validate and print merged Compose configuration
	$(COMPOSE) config

.PHONY: config
config: compose-config ## Alias for `make compose-config`

.PHONY: compose-ps
compose-ps: ## List Compose services and status
	$(COMPOSE) ps

.PHONY: ps
ps: compose-ps ## Alias for `make compose-ps`

.PHONY: up
up: secrets-check ## Start PostGIS only (run the API with `make run` on the host)
	$(COMPOSE) up -d

.PHONY: compose-up
compose-up: up ## Alias for `make up`

.PHONY: up-build
up-build: secrets-check ## Start API + PostGIS in Docker (builds app image; requires profile)
	$(COMPOSE) --profile $(COMPOSE_PROFILE_STACK) up -d --build

.PHONY: compose-up-build
compose-up-build: up-build ## Alias for `make up-build`

.PHONY: down
down: ## Stop containers and remove networks (keep volumes)
	$(COMPOSE) down

.PHONY: compose-down
compose-down: down ## Alias for `make down`

.PHONY: down-volumes
down-volumes: ## Stop containers and remove volumes (wipes PostGIS data)
	$(COMPOSE) down -v

.PHONY: compose-down-volumes
compose-down-volumes: down-volumes ## Alias for `make down-volumes`

.PHONY: logs
logs: ## Follow logs for all Compose services
	$(COMPOSE) logs -f

.PHONY: compose-logs
compose-logs: logs ## Alias for `make logs`

.PHONY: logs-app
logs-app: ## Follow logs for the app service only
	$(COMPOSE) logs -f app

.PHONY: logs-db
logs-db: ## Follow logs for the postgres service only
	$(COMPOSE) logs -f postgres

.PHONY: restart
restart: ## Restart running Compose containers (keeps whatever is currently up)
	$(COMPOSE) restart

.PHONY: compose-restart
compose-restart: restart ## Alias for `make restart`

.PHONY: pull
pull: ## Pull images declared in the compose file (e.g. PostGIS)
	$(COMPOSE) pull

.PHONY: compose-pull
compose-pull: pull ## Alias for `make pull`

# =============================================================================
# Local development shortcuts
# =============================================================================

.PHONY: dev-db
dev-db: secrets-check ## Start only PostGIS (then `make run` on the host)
	$(COMPOSE) up -d postgres

.PHONY: postgres-up
postgres-up: dev-db ## Alias for `make dev-db`

.PHONY: dev-db-logs
dev-db-logs: ## Follow PostGIS logs
	$(COMPOSE) logs -f postgres

.PHONY: stop-db
stop-db: ## Stop the postgres service without removing the container
	$(COMPOSE) stop postgres

.PHONY: postgres-down
postgres-down: stop-db ## Alias for `make stop-db`

# =============================================================================
# HTTP probes against http://127.0.0.1:APP_PORT (app must listen on host)
# =============================================================================

.PHONY: health
health: ## GET /actuator/health (uses curl)
	@command -v curl >/dev/null 2>&1 && curl -fsS "http://127.0.0.1:$(APP_PORT)/actuator/health" && printf '\n' || \
	  (printf '%s\n' "curl missing or request failed — is the app on port $(APP_PORT)?" >&2; exit 1)

.PHONY: curl-health
curl-health: health ## Alias for `make health`

.PHONY: readiness
readiness: ## GET /actuator/health/readiness
	@command -v curl >/dev/null 2>&1 && curl -fsS "http://127.0.0.1:$(APP_PORT)/actuator/health/readiness" && printf '\n' || exit 1

# =============================================================================
# Cleanup
# =============================================================================

.PHONY: clean
clean: down clean-gradle ## `make down` plus Gradle clean (keep OCI images / volumes)

.PHONY: clean-all
clean-all: down-volumes clean-gradle docker-rmi-local ## Remove volumes, gradle clean, untag IMAGE_NAME
