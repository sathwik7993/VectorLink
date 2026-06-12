# ==============================================================================
# Stage 1: Build
# ==============================================================================
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /app

# Copy POM first to cache dependency resolution as a separate Docker layer.
# Dependencies only re-download when pom.xml changes, not on every src change.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build the fat JAR
COPY src ./src
RUN mvn package -DskipTests -B

# ==============================================================================
# Stage 2: Runtime
# ==============================================================================
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="VectorLink Team"
LABEL description="VectorLink - High-performance URL shortener with analytics"

WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the application port
EXPOSE 8080

# Container-aware JVM memory settings:
#   -XX:+UseContainerSupport  : JVM respects cgroup memory limits
#   -XX:MaxRAMPercentage=75.0 : Use at most 75% of container memory for heap
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
