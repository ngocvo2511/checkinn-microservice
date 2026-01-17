# Stage 1: Build (Vẫn dùng bản thường để hỗ trợ gRPC)
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src

# --- SỬA DÒNG NÀY ---
# Thay -DskipTests bằng -Dmaven.test.skip=true
# Lệnh này sẽ bỏ qua luôn việc COMPILE file test, tránh lỗi thiếu thư viện
RUN mvn clean package -Dmaven.test.skip=true

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]