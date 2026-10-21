# Multi-stage build for BFlow Backend Application

# Stage 1: Build stage
FROM maven:3.9-eclipse-temurin-21-noble AS build

WORKDIR /app

# Copy Maven files for dependency caching
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-noble

WORKDIR /app

# Create a non-root user for security
RUN groupadd --system spring \
    && useradd --system --gid spring --create-home spring

# Copy the built JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Create the agent directory explicitly: --chmod on ADD/COPY also applies to auto-created parent dirs (mode 644 is not traversable by the non-root user)
RUN mkdir -p /opt/otel && chmod 755 /opt/otel
ARG ADOT_VERSION=2.31.0
ARG ADOT_SHA256=4e0f171c6d935f0b9ae5f66b28b0b2dae5e2ac1261fc3e3ffc396ef1e500f4fc
ADD --checksum=sha256:${ADOT_SHA256} --chmod=644 \
    https://github.com/aws-observability/aws-otel-java-instrumentation/releases/download/v${ADOT_VERSION}/aws-opentelemetry-agent.jar \
    /opt/otel/aws-opentelemetry-agent.jar

# Change ownership to non-root user
RUN chown spring:spring app.jar \
    && chown spring:spring /opt/otel/aws-opentelemetry-agent.jar

# Switch to non-root user
USER spring:spring
# Fail the build, not production, if the non-root user cannot read the agent
RUN test -r /opt/otel/aws-opentelemetry-agent.jar

# Expose the application port
EXPOSE 8080

# Run the application; the agent is activated only when JAVA_TOOL_OPTIONS includes
# -javaagent:/opt/otel/aws-opentelemetry-agent.jar.
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
