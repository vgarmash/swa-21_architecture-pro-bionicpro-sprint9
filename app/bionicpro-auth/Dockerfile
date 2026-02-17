# Build stage
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN apk add --no-cache maven

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S bionicpro && adduser -S bionicpro -G bionicpro

# Copy built artifact
COPY --from=builder /app/target/*.jar app.jar

# Set ownership
RUN chown -R bionicpro:bionicpro /app

# Switch to non-root user
USER bionicpro

# Expose port
EXPOSE 8000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8000/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
