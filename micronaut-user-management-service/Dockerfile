# ---- build ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Wrapper and build scripts first, so dependency resolution caches independently
# of source edits.
COPY gradlew settings.gradle build.gradle gradle.properties micronaut-cli.yml ./
COPY gradle gradle

# gradle.properties pins org.gradle.java.home to a host SDKMAN path so local
# builds find a JDK 25 (the default `java` on the dev machine is 23). Inside this
# image JAVA_HOME is already 25, and that host path does not exist, so drop it.
RUN sed -i '/org.gradle.java.home/d' gradle.properties

RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true

COPY src src
# buildLayers emits app/application.jar next to the libs/ and resources/ directories
# its manifest Class-Path points at. The plain -runner.jar is not self-contained.
RUN ./gradlew --no-daemon clean buildLayers -x test

# ---- runtime ----
FROM eclipse-temurin:25-jre AS runtime

# curl is not in the JRE image but the HEALTHCHECK below needs it.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 1001 app \
 && useradd --system --uid 1001 --gid app --create-home app

WORKDIR /app
# Ordered least- to most-frequently changed, so dependency layers stay cached.
COPY --from=build --chown=app:app /build/build/docker/main/layers/libs      /app/libs
COPY --from=build --chown=app:app /build/build/docker/main/layers/resources /app/resources
COPY --from=build --chown=app:app /build/build/docker/main/layers/app       /app/

USER app
EXPOSE 8080

# The health endpoint is anonymous (see application.yml intercept-url-map), so it
# needs no token to answer.
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
  CMD ["sh", "-c", "curl -fsS http://localhost:8080/health > /dev/null || exit 1"]

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/application.jar"]
