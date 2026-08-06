# ─────────────────────────────────────────────────────────────
#  Stage 1 — Build
#  Uses Maven + JDK 17 to produce a fat JAR
# ─────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy dependency manifest first so Docker caches the dependency
# layer independently of source changes
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ─────────────────────────────────────────────────────────────
#  Stage 2 — Run
#  Slim JRE-only image — no Maven, no source code
# ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Non-root user for security
RUN addgroup -S trading && adduser -S trading -G trading
USER trading

# Copy JAR from builder stage
COPY --from=builder /app/target/fix-trading-simulator-1.0.0.jar app.jar

# Expose REST API and FIX session ports
EXPOSE 8080 9878

# Health check via Spring Actuator
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
