# Stage 1: Build JAR with Maven
FROM eclipse-temurin:17-jdk AS builder
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*
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
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system fintech && useradd --system --gid fintech fintech
COPY --from=builder /app/stream-processing-service/target/stream-processing-service-*.jar /app/stream-processing-service.jar
RUN mkdir -p /app/logs && chown -R fintech:fintech /app
USER fintech
ENTRYPOINT ["java", "-jar", "/app/stream-processing-service.jar"]