# Stage 1: Build JAR with Maven
FROM eclipse-temurin:17-jdk-alpine AS builder
RUN apk add --no-cache maven
WORKDIR /app
COPY pom.xml .
COPY data-ingestion-service/pom.xml data-ingestion-service/pom.xml
COPY stream-processing-service/pom.xml stream-processing-service/pom.xml
COPY feature-store-service/pom.xml feature-store-service/pom.xml
COPY ml-inference-service/pom.xml ml-inference-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY monitoring-service/pom.xml monitoring-service/pom.xml
COPY data-ingestion-service/src data-ingestion-service/src
COPY stream-processing-service/src stream-processing-service/src
COPY feature-store-service/src feature-store-service/src
COPY ml-inference-service/src ml-inference-service/src
COPY api-gateway/src api-gateway/src
COPY monitoring-service/src monitoring-service/src
RUN mvn -f pom.xml clean package -DskipTests 2>&1 | tail -30

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S fintech && adduser -S fintech -G fintech
COPY --from=builder /app/feature-store-service/target/feature-store-service-*.jar /app/feature-store-service.jar
RUN mkdir -p /app/logs && chown -R fintech:fintech /app
USER fintech
ENTRYPOINT ["java", "-jar", "/app/feature-store-service.jar"]