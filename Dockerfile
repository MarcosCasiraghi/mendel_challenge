# syntax=docker/dockerfile:1.7
#
# Multi-stage build for the Mendel transactions service.
#
# Stages:
#   deps     - resolves Maven dependencies into the local repo so they can
#              be cached independently of source changes.
#   build    - compiles the application and packages the fat jar.
#   test     - runs the full Maven test suite. Targeted explicitly with
#              `docker build --target test .`; it is NOT part of the default
#              build path so production images are not slowed down by tests.
#   runtime  - minimal JRE image that actually runs in production.
#
# See DOCKER.md for the rationale behind each decision.

# ---------- Stage: deps ----------
FROM eclipse-temurin:17-jdk-jammy AS deps
WORKDIR /workspace

# Copy only the files needed to resolve dependencies. Keeping this layer
# separate from `src/` means a code change does not invalidate the (slow)
# Maven download layer.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
 && ./mvnw -B -ntp dependency:go-offline

# ---------- Stage: build ----------
FROM deps AS build
WORKDIR /workspace
COPY src ./src
# Tests are intentionally skipped here. They are run in the dedicated
# `test` stage so that:
#   * production image builds stay fast and deterministic, and
#   * a broken or failing test does not block packaging the production
#     artifact (e.g. hotfix path with a documented override).
#
# Note: `-Dmaven.test.skip=true` is used instead of `-DskipTests`.
# `-DskipTests` skips test *execution* but still runs `testCompile`, so
# a test file that fails to compile would break the production build.
# `maven.test.skip` skips compilation as well, which is what we want
# here — the `test` stage is the single place that compiles and runs
# tests.
RUN ./mvnw -B -ntp -Dmaven.test.skip=true package \
 && cp target/*.jar /workspace/app.jar

# ---------- Stage: test ----------
# Run with:  docker build --target test --progress=plain .
# The stage produces no useful artifact; its purpose is the side effect
# of executing `mvn test` inside a clean, reproducible environment.
FROM deps AS test
WORKDIR /workspace
COPY src ./src
RUN ./mvnw -B -ntp test

# ---------- Stage: runtime ----------
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# Run as an unprivileged user. The Spring process never needs root, and
# dropping privileges limits the blast radius of a container escape or
# RCE inside the JVM.
RUN groupadd --system --gid 1001 spring \
 && useradd  --system --uid 1001 --gid spring --home-dir /app --shell /usr/sbin/nologin spring

COPY --from=build --chown=spring:spring /workspace/app.jar /app/app.jar

USER spring:spring

# Spring Boot's default port. Documented here so `docker run -P` works
# without surprises; the actual binding is still up to the operator.
EXPOSE 8080

# JAVA_TOOL_OPTIONS is read by the JVM itself at startup, so heap, GC,
# and agent flags can be tuned at deploy time without rebuilding the
# image. Example: `docker run -e JAVA_TOOL_OPTIONS="-Xmx512m" ...`.
ENV JAVA_TOOL_OPTIONS=""

# Exec form so the JVM is PID 1 and receives SIGTERM directly from the
# container runtime. This lets Spring Boot run its shutdown hooks cleanly
# on `docker stop` instead of being killed after the 10-second grace.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
