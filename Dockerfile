# ============================================
# Stage 1: Build with Maven
# ============================================
FROM maven:3.8.8-eclipse-temurin-8 AS builder

WORKDIR /build
COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# ============================================
# Stage 2: Run with minimal JRE
# ============================================
FROM eclipse-temurin:8-jre-alpine

WORKDIR /app

# 从 builder 阶段复制打好包的 JAR
COPY --from=builder /build/target/ai-typewriter-server-1.0.0.jar app.jar

# 暴露端口（与 application.yml 中的 server.port 一致）
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:8080/api/health || exit 1

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]