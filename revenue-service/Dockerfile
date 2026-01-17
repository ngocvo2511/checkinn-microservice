# Stage 1: Build (Dùng bản Debian để hỗ trợ biên dịch gRPC/Protobuf)
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Bỏ qua test để tránh lỗi thiếu thư viện và build nhanh hơn
RUN mvn clean package -Dmaven.test.skip=true

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Revenue service chạy báo cáo nên cấp RAM ổn định
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]