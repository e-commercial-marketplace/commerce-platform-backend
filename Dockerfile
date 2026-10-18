# syntax=docker/dockerfile:1
#
# Multi-stage build cho backend marketplace:
#   stage 1 (build)   - Maven + JDK 21 đóng gói fat jar, không cần JDK trên máy chạy.
#   stage 2 (runtime) - chỉ copy fat jar sang JRE 21 Alpine, chạy bằng user không phải root.
# Build:  docker build -t marketplace-backend:local .
# Hoặc:   docker compose up -d --build   (dùng file docker-compose.yml trong thư mục này)

# ---------------------------------------------------------------------------
# Stage 1: build
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Copy pom.xml trước để layer tải dependency được cache lại khi chỉ đổi source code.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests dependency:go-offline

# Sau đó mới copy source và đóng gói. Test source vẫn được compile (bắt lỗi sớm)
# nhưng không chạy trong image build; test thật chạy ở local/CI bằng `mvn verify`.
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests package

# ---------------------------------------------------------------------------
# Stage 2: runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Tạo user không phải root và chỉ sở hữu thư mục ứng dụng.
RUN addgroup -S app && adduser -S -G app app

WORKDIR /app

# target/*.jar khớp đúng fat jar do spring-boot-maven-plugin tạo ra
# (file gốc đã được đổi tên thành *.jar.original nên không bị copy nhầm).
COPY --from=build --chown=app:app /build/target/*.jar /app/app.jar

# JAVA_OPTS có thể override khi chạy container (ví dụ thêm -Xmx, -Dspring.profiles.active).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75" \
    LANG=C.UTF-8

EXPOSE 8080

# Healthcheck dựa trên actuator để Docker/Compose biết khi nào API sẵn sàng.
# Dùng SERVER_PORT nếu được override khi chạy container, mặc định 8080.
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- "http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health" | grep -q '"status":"UP"' || exit 1

USER app

# exec để java thay thế shell, nhận đúng tín hiệu SIGTERM khi compose stop.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
