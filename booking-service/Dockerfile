# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Skip test để build nhanh và tránh lỗi thư viện test
RUN mvn clean package -Dmaven.test.skip=true

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Booking service cần xử lý thanh toán, giữ kết nối -> Cấp RAM ổn
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]