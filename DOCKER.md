# Docker — Build, Run, Design Decisions

This document explains how the service is containerised and the rationale behind every choice in the `Dockerfile`. It is meant to be readable on its own; the architecture of the service itself lives in `README.md`.

## Quick reference

Build the production image:

```bash
docker build -t mendel-challenge:latest .
```

Run it:

```bash
docker run --rm -p 8080:8080 mendel-challenge:latest
```

Run the test suite inside a clean container (no Maven or JDK required on the host):

```bash
docker build --target test --progress=plain .
```

Pass JVM flags at runtime without rebuilding:

```bash
docker run --rm -p 8080:8080 -e JAVA_TOOL_OPTIONS="-Xmx512m -XX:+UseG1GC" mendel-challenge:latest
```

## Design decisions

### Multi-stage build

The `Dockerfile` is split into four stages — `deps`, `build`, `test`, `runtime` — instead of one monolithic image. The shipping image only inherits from `runtime`, which means the JDK, Maven wrapper, source tree, and `~/.m2` cache never end up in the artifact that runs in production. The final image is a few hundred megabytes smaller and has a much smaller attack surface than a single-stage build would produce.

### Separate `deps` stage for dependency caching

`pom.xml` and the Maven wrapper are copied in before the source tree, and `mvnw dependency:go-offline` runs in its own layer. Docker's layer cache only invalidates layers below the first change, so as long as `pom.xml` is unchanged, a code edit reuses the cached dependency layer instead of re-downloading every Spring Boot artifact. On a cold build the dependency download dominates wall time, so this caching is the difference between a 5-second and a 90-second iteration loop.

### JDK for building, JRE for running

The build stages use `eclipse-temurin:17-jdk-jammy` because compiling and running tests both need `javac`. The runtime stage uses `eclipse-temurin:17-jre-jammy`, which omits the compiler, the JDK tooling (`javac`, `jshell`, `jlink`, `jstack`, `jmap`), and the source jars. There is no reason for a production container to ship a compiler, and removing it both saves space and removes tooling that would be useful to an attacker who lands a shell inside the container.

Eclipse Temurin specifically was chosen because it is the reference OpenJDK distribution maintained by the Adoptium project, it is well-supported on Linux, and it publishes images on Docker Hub with predictable tags. The `-jammy` variant (Ubuntu 22.04) is preferred over `-alpine` because Alpine uses `musl` libc, which has occasional compatibility quirks with native libraries the JVM may load.

### Java 17

The project's `pom.xml` declares `<java.version>17</java.version>`, so the container has to match. The challenge required Java 11 or higher; 17 is the latest LTS that the chosen Spring Boot 4.0.6 line is built against, so it gives a longer support window than 11 without forcing a non-LTS release.

### Tests run in a dedicated stage, not as part of the production build

The `build` stage runs `mvn package -Dmaven.test.skip=true`. Tests run only in the `test` stage, which is targeted explicitly with `docker build --target test`. This is deliberate:

- The production image build does not pay the cost of running the test suite on every rebuild. A hotfix can ship through the same `Dockerfile` without needing tests to pass first (and the CI pipeline can still gate on `--target test` before promoting the image).
- The test stage shares the cached `deps` stage with the build stage, so running tests does not re-resolve dependencies.
- Tests run inside the same JDK image they would be built with, which makes failures reproducible: "passes on my machine, fails in CI" cannot be a JDK-version problem.

The flag is `-Dmaven.test.skip=true` rather than the more common `-DskipTests`. The latter skips test *execution* but still runs `testCompile`, so a test file with a stale import or a missing symbol would break the production build even though tests are nominally skipped. `maven.test.skip` skips compilation as well, which is the correct behaviour here: the production stage builds the jar, the `test` stage owns everything to do with tests.

This is the answer to the question in the title — see the next section for why this is preferred over a second `Dockerfile`.

### Non-root runtime user

The runtime stage creates a `spring` system user and group (uid/gid 1001) and `USER`-switches before the entrypoint runs. Containers default to running as `root`, and a Spring Boot app has no reason to need root privileges. Running unprivileged means that an attacker who achieves RCE inside the JVM cannot trivially modify system files, install packages, or use raw sockets, and any container break-out lands in a less privileged context. Using a fixed uid (1001) rather than letting the system pick one keeps the image's filesystem permissions stable across rebuilds, which matters when the image is mounted into Kubernetes with `runAsUser` enforced.

### `JAVA_TOOL_OPTIONS` instead of a wrapping shell

The entrypoint is the exec form `ENTRYPOINT ["java", "-jar", "/app/app.jar"]`. JVM flags are passed at runtime through the `JAVA_TOOL_OPTIONS` environment variable, which the JVM reads automatically at startup. Two benefits over a `sh -c "java $JAVA_OPTS -jar ..."` pattern:

- The JVM is PID 1 inside the container, so it receives `SIGTERM` directly from `docker stop`. This lets Spring Boot's shutdown hooks run within the default 10-second grace period instead of being `SIGKILL`-ed after the timeout.
- There is no shell process to fork, parse, or expand variables, which removes a small class of injection bugs around quoting.

### Build context hygiene with `.dockerignore`

`.dockerignore` excludes `target/`, `.git/`, `.idea/`, `*.pdf`, and the markdown documentation. This matters for two reasons: the build context is what Docker uploads to the daemon, so trimming it makes every build faster, and it guarantees that a stale local `target/` cannot accidentally end up shadowing a freshly built jar inside the image. Excluding `*.pdf` specifically keeps the challenge brief out of the image, which is the kind of artefact that should never end up in a deployed container.

### `EXPOSE 8080`

`EXPOSE` is documentation for the operator — it does not actually publish the port. Spring Boot binds to `8080` by default, and declaring it here means `docker run -P` will publish the right port automatically and reading the image with `docker inspect` shows the contract without needing to read the source.

## Why there is no second `Dockerfile.test`

It is a reasonable question — many projects do ship a `Dockerfile.test`. For this service, a separate file would only duplicate the `deps` stage that the main `Dockerfile` already needs. The multi-stage build solves the same problem more cleanly:

- One source of truth for the base image, Java version, and Maven wrapper invocation.
- The `test` stage reuses the cached `deps` layer, so running tests is fast after the first build.
- A bump to Java 21 or a new Temurin tag is a one-line change instead of a two-file change that risks drifting.

A separate `Dockerfile.test` is the right call when the test environment legitimately differs from production — for example, when tests need a database, an LDAP server, a sidecar, or extra OS packages that should not be in the production image. This service is fully in-memory and its tests use `MockMvc` against an embedded Spring context, so there is no legitimate divergence to model. The `--target test` stage is preferred.

If the project later grows real integration dependencies (Testcontainers spinning up Postgres, Kafka, etc.), the right move would be a `docker-compose.test.yml` that orchestrates those services around the existing `test` stage — still no second `Dockerfile` needed.

## Possible future improvements

- **Layered jars.** Spring Boot supports `spring-boot-jarmode-layertools` to extract a fat jar into separate dependency / loader / application layers, which gives even better Docker layer caching when only application code changes. Skipped here because the gain is marginal for a service this small.
- **Buildpacks.** `./mvnw spring-boot:build-image` produces an OCI image without any `Dockerfile` at all, using Paketo buildpacks. Skipped because an explicit `Dockerfile` is more transparent for a code-review-style challenge — every choice is visible in the file.
- **Distroless runtime.** `gcr.io/distroless/java17-debian12` would strip the runtime image down further. Trade-off: no shell makes debugging in production harder, which is the wrong default for a service that is still being evaluated.
- **Healthcheck.** Adding `spring-boot-starter-actuator` and a `HEALTHCHECK` instruction would let orchestrators detect a wedged JVM. Skipped because the challenge does not include actuator and adding it just to support the healthcheck would expand the dependency footprint.
