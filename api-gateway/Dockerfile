# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Gateway cần xử lý mạng nhiều, nên cấp RAM thoải mái xíu (256-512MB)
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]