# Troubleshooting Guide

## Common Issues and Solutions

---

## Kafka Brokers Not Starting

**Symptom:** Kafka pods stuck in `Creating` or `CrashLoopBackOff` state.

**Diagnosis:**
```bash
kubectl logs -n fintech kafka-0
kubectl describe pod -n fintech kafka-0
```

**Common Causes:**
1. Zookeeper not ready
   ```bash
   kubectllogs -n fintech zookeeper-0
   ```
   **Fix:** Wait for Zookeeper to be healthy before Kafka.

2. Insufficient memory
   ```bash
   kubectl describe nodes | grep -A 5 "allocatable"
   ```
   **Fix:** Increase Docker/K8s memory allocation. Minimum 4GB recommended.

3. Port conflicts
   ```bash
   kubectl get svc -n fintech | grep 9092
   ```
   **Fix:** Ensure port 9092 is not in use by another service.

---

## PostgreSQL Connection Timeout

**Symptom:** Connection refused, HikariCP pool exhausted errors.

**Diagnosis:**
```bash
kubectl exec -n fintech postgres-0 -- pg_isready -U postgres
kubectl logs -n fintech -l app=feature-store | grep -i " connection\|pool\|timeout"
```

**Solutions:**
1. Verify PostgreSQL is running:
   ```bash
   kubectl get pods -n fintech -l app=postgres
   ```

2. Check connection string in config:
   ```bash
   kubectl get configmap -n fintech fintech-config -o yaml | grep POSTGRES
   ```

3. Increase connection pool:
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 30
         connection-timeout: 30000
   ```

4. Test connectivity:
   ```bash
   kubectl run -n fintech postgres-client --rm -it --image=postgres:15-alpine -- \
     psql -h postgres -U postgres -d financial_data -c "SELECT 1;"
   ```

---

## Redis Cache Misses

**Symptom:** Slow feature retrieval, cache misses in logs.

**Diagnosis:**
```bash
kubectl exec -n fintech redis-0 -- redis-cli -a redis_password KEYS "features:*"
kubectl exec -n fintech redis-0 -- redis-cli -a redis_password DBSIZE
kubectl logs -n fintech -l app=feature-store | grep -i "cache\|redis"
```

**Solutions:**
1. Check Redis is running:
   ```bash
   kubectl get pods -n fintech -l app=redis
   ```

2. Verify password:
   ```bash
   kubectl get secret -n fintech fintech-secrets -o yaml | grep redis
   ```

3. Check memory pressure:
   ```bash
   kubectl exec -n fintech redis-0 -- redis-cli -a redis_password INFO memory
   ```
   If `maxmemory_policy` is `allkeys-lru`, evicted keys are expected.

4. Warm the cache:
   ```bash
   curl http://localhost:8084/features/BTCUSD/refresh
   ```

---

## Model Prediction Latency High

**Symptom:** P95 latency >200ms, queue buildup.

**Diagnosis:**
```bash
curl http://localhost:8083/actuator/metrics/inference.queue.depth
curl http://localhost:8083/actuator/metrics/prediction_latency_seconds
```

**Solutions:**
1. Scale ML inference service:
   ```bash
   kubectl scale deployment ml-inference -n fintech --replicas=5
   ```

2. Enable GPU acceleration (if available):
   ```yaml
   resources:
     limits:
       nvidia.com/gpu: 1
   ```

3. Reduce model complexity temporarily:
   - Lower hidden layer size
   - Reduce training epochs

4. Check for memory pressure:
   ```bash
   kubectl top pods -n fintech -l app=ml-inference
   ```

---

## Kafka Consumer Lag

**Symptom:** Lag metric in Prometheus > 1000 for extended period.

**Diagnosis:**
```bash
kubectl logs -n fintech -l app=stream-processing | grep -i lag
curl http://localhost:9090/api/v1/query?query=kafka_consumer_lag
```

**Solutions:**
1. Increase stream processing replicas:
   ```bash
   kubectl scale deployment stream-processing -n fintech --replicas=5
   ```

2. Check consumer group status:
   ```bash
   kafka-consumer-groups --bootstrap-server kafka:9092 \
     --group financial-aggregator-group --describe
   ```

3. Increase Kafka partition count (for higher parallelism):
   ```bash
   kafka-topics --bootstrap-server kafka:9092 \
     --alter --topic raw-ticks --partitions 64
   ```

---

## Exchange Connectors Not Receiving Data

**Symptom:** No tick data in `raw-ticks` topic.

**Diagnosis:**
```bash
kubectl logs -n fintech -l app=data-ingestion | grep -i "tick\|error\|connect"
# Check Kafka topic has messages
kafka-console-consumer --bootstrap-server kafka:9092 --topic raw-ticks --from-beginning --max-messages 10
```

**Solutions:**
1. Verify API keys are set:
   ```bash
   kubectl get secret -n fintech fintech-secrets -o yaml | grep -i api
   ```

2. Test network connectivity from ingestion pod:
   ```bash
   kubectl exec -n fintech -it data-ingestion-0 -- \
     curl -s https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT
   ```

3. Check rate limiting — some exchanges limit WebSocket connections

---

## ML Model Not Training

**Symptom:** Training endpoint returns but no model updates.

**Diagnosis:**
```bash
kubectl logs -n fintech -l app=ml-inference | grep -i "train\|error"
curl http://localhost:8083/api/v1/models/BTCUSD/metrics
```

**Solutions:**
1. Check feature data available:
   ```bash
   curl "http://localhost:8084/api/v1/features/BTCUSD?lookbackSeconds=86400" | jq length
   ```

2. Verify minimum data requirement (need 60+ samples for training):
   ```bash
   # Should return > 60 features
   ```

3. Check database connectivity:
   ```bash
   kubectl exec -n fintech ml-inference-0 -- \
     curl -s http://postgres:5432/actuator/health
   ```

4. Check disk space for model storage:
   ```bash
   kubectl exec -n fintech ml-inference-0 -- df -h /models
   ```

---

## Docker Compose Not Starting

**Symptom:** `docker-compose up` fails or containers exit immediately.

**Solutions:**
1. Check Docker resources:
   ```bash
   docker stats
   # Ensure at least 8GB RAM available for all containers
   ```

2. Check for port conflicts:
   ```bash
   netstat -an | grep -E "9092|5432|6379|8080"
   ```

3. Clean and restart:
   ```bash
   docker-compose down -v
   docker volume prune -f
   docker-compose up -d
   docker-compose logs -f
   ```

---

## Gradle/Maven Build Failures

**Symptom:** `mvn clean package` fails.

**Solutions:**
1. Ensure Java 17 is installed:
   ```bash
   java -version
   # Must be 17 or higher
   ```

2. Clean and retry:
   ```bash
   mvn clean package -DskipTests -U
   ```

3. For deeplearning4j issues, check your CUDA version:
   ```bash
   nvcc --version  # If using GPU support
   ```

## Getting Additional Help

- Check service logs with `kubectl logs -n fintech -l app=<service-name>`
- Prometheus metrics: http://localhost:9090
- Grafana dashboards: http://localhost:3000
- Kafka UI: http://localhost:8081
- Health endpoints: `curl http://localhost:PORT/actuator/health`