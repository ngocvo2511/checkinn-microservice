# --- Stage 1: Build bằng Maven & Java 21 ---
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Build ra file jar, bỏ qua test để tiết kiệm thời gian
RUN mvn clean package -DskipTests

# --- Stage 2: Chạy bằng Java 21 JRE (Nhẹ) ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Cấu hình timezone VN (tuỳ chọn, nhưng nên có)
RUN apk add --no-cache tzdata
ENV TZ=Asia/Ho_Chi_Minh

# Chạy app với giới hạn RAM an toàn
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]