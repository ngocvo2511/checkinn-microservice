# --- SỬA DÒNG NÀY (Bỏ chữ -alpine đi) ---
# Dùng bản thường (Debian based) để có glibc chạy được protoc
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run thì vẫn dùng Alpine được cho nhẹ (vì lúc này đã có file .jar rồi)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]