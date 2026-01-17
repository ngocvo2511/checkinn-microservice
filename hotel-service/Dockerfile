# Stage 1: Build (Dùng bản chuẩn để hỗ trợ Protobuf/gRPC)
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Hotel Service xử lý nghiệp vụ chính -> Cấp RAM ổn định
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]