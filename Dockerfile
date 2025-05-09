FROM eclipse-temurin:17-jdk-alpine as build
WORKDIR /app

# Copy POM and source code
COPY pom.xml .
COPY src ./src

# Build the application
RUN apk add --no-cache maven && \
    mvn clean package && \
    rm -rf /root/.m2

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create directories
RUN mkdir -p /app/logs /app/cache /app/ledger/blocks /app/metrics /app/config

# Copy the built jar from the build stage
COPY --from=build /app/target/jcachenetdl-1.0-SNAPSHOT-jar-with-dependencies.jar /app/jcachenetdl.jar

# Expose ports
EXPOSE 8080
EXPOSE 8081

# Set environment variables
ENV NODE_PORT=8080
ENV API_PORT=8081
ENV SECURITY_ENABLED=false

# Set entrypoint
ENTRYPOINT ["java", "-jar", "/app/jcachenetdl.jar"]

# Default command: run in background mode
CMD ["--background"]
