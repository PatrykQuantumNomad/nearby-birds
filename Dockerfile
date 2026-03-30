# syntax=docker/dockerfile:1
#
# nearby-birds — production-oriented JVM image for the Spring Boot API.
#
# Stages:
#   1. build  — Gradle bootJar (JDK, full toolchain).
#   2. final   — JRE-only runtime: non-root (UID/GID 10001), tini as PID 1,
#                HEALTHCHECK via actuator readiness (wget, from base image).
#
# Build arguments (optional overrides; both declared before the first FROM per Docker rules):
#   TEMURIN_BUILD_IMAGE    — builder base (default: Eclipse Temurin 25 JDK Alpine 3.23, digest-pinned).
#   TEMURIN_RUNTIME_IMAGE — runtime base (default: Eclipse Temurin 25 JRE Alpine 3.23, digest-pinned).
#
# Examples:
#   docker build --build-arg TEMURIN_RUNTIME_IMAGE=eclipse-temurin:25-jre-alpine-3.23 .
#   docker build --build-arg TEMURIN_BUILD_IMAGE=eclipse-temurin:25-jdk-alpine-3.23 \
#                --build-arg TEMURIN_RUNTIME_IMAGE=eclipse-temurin:25-jre-alpine-3.23 .
#
# Refresh digests after tag updates: docker pull <image> && docker inspect --format '{{index .RepoDigests 0}}' <image>
#
# Note: ARGs used in any FROM must be declared before the first FROM (Dockerfile rule).

ARG TEMURIN_BUILD_IMAGE=eclipse-temurin:25-jdk-alpine-3.23@sha256:da683f4f02f9427597d8fa162b73b8222fe08596dcebaf23e4399576ff8b037e
ARG TEMURIN_RUNTIME_IMAGE=eclipse-temurin:25-jre-alpine-3.23@sha256:f10d6259d0798c1e12179b6bf3b63cea0d6843f7b09c9f9c9c422c50e44379ec

FROM ${TEMURIN_BUILD_IMAGE} AS build
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

FROM ${TEMURIN_RUNTIME_IMAGE}
RUN apk add --no-cache tini=0.19.0-r3 \
  && addgroup -g 10001 -S app \
  && adduser -u 10001 -S -G app -s /sbin/nologin app \
  && mkdir -p /app \
  && chown app:app /app
WORKDIR /app
COPY --from=build --chown=10001:10001 /app/build/libs/*.jar app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD /bin/sh -c 'wget -qO- http://127.0.0.1:8080/actuator/health/readiness | grep -q UP || exit 1'
ENTRYPOINT ["/sbin/tini", "--"]
CMD ["java", "-jar", "app.jar"]
