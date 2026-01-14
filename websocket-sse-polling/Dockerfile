# Build stage
FROM gradle:8.11-jdk21 AS builder

WORKDIR /app

# Copy gradle wrapper and configuration files
COPY gradle ./gradle
COPY gradlew ./
COPY settings.gradle.kts ./
COPY build.gradle.kts ./

# Copy all subprojects
COPY websocket-sse-polling/build.gradle.kts ./websocket-sse-polling/
COPY websocket-sse-polling/src ./websocket-sse-polling/src

# Build only websocket-sse-polling module
RUN ./gradlew :websocket-sse-polling:bootJar -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install wget for healthcheck
RUN apk add --no-cache wget

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring

# Copy jar from builder
COPY --from=builder /app/websocket-sse-polling/build/libs/*.jar app.jar

# Change ownership
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
