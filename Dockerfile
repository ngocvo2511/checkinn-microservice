# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /workspace
COPY ${SERVICE}/pom.xml ./pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline
COPY ${SERVICE}/src ./src
# Runtime packaging is separate from the repository's test suite.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -Dmaven.test.skip=true package && cp target/*.jar /app.jar

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --uid 10001 app
WORKDIR /app
RUN mkdir -p /app/logs && chown -R app:app /app
COPY --from=build --chown=app:app /app.jar ./app.jar
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
