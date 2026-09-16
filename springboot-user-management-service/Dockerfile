# ---- build ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle

# gradle.properties pins org.gradle.java.home to a host SDKMAN path so local builds
# find a JDK 25. Inside this image JAVA_HOME is already 25 and that path does not
# exist, so drop it.
RUN sed -i '/org.gradle.java.home/d' gradle.properties

RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- runtime ----
FROM eclipse-temurin:25-jre AS runtime

# curl is not in the JRE image but the HEALTHCHECK below needs it.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 1001 app \
 && useradd --system --uid 1001 --gid app --create-home app

WORKDIR /app
# Spring Boot's bootJar is self-contained, so unlike the Micronaut image this is a
# single COPY with no layer directories to reassemble.
COPY --from=build --chown=app:app /build/build/libs/springboot-user-service-1.0.jar app.jar

USER app
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
  CMD ["sh", "-c", "curl -fsS http://localhost:8080/health > /dev/null || exit 1"]

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
