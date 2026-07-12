# Production Deployment Guide

## Prerequisites

- Kubernetes 1.27+
- kubectl configured with cluster access
- Helm 3.11+ (optional, for Helm-based deployment)
- Docker Registry access for pushing images
- Minimum 16GB RAM across cluster nodes

## Pre-Deployment Checklist

### 1. Secrets Configuration

Update `k8s/secrets.yaml` with production values:

```bash
# Generate secrets
kubectl create secret generic fintech-secrets \
  --namespace=fintech \
  --from-literal=postgres-password="$(openssl rand -base64 32)" \
  --from-literal=redis-password="$(openssl rand -base64 32)" \
  --from-literal=jwt-secret="$(openssl rand -base64 32)" \
  --from-literal=grafana-admin-password="$(openssl rand -base64 16)"
```

### 2. Build and Push Images

```bash
export REGISTRY="your-registry.azurecr.io"
export VERSION="1.0.0"

# Build all services
mvn clean package -DskipTests -T 1C

# Tag and push
for service in data-ingestion stream-processing feature-store ml-inference api-gateway monitoring; do
  docker build -t ${REGISTRY}/${service}:${VERSION} ${service}/
  docker push ${REGISTRY}/${service}:${VERSION}
done
```

### 3. Infrastructure Setup

Create the namespace and apply infrastructure:
```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/statefulsets/redis.yaml
kubectl apply -f k8s/statefulsets/postgres.yaml
kubectl apply -f k8s/statefulsets/kafka.yaml
```

Wait for storage to provision:
```bash
kubectl wait --for=condition=Ready pod -l app=postgres -n fintech --timeout=120s
kubectl wait --for=condition=Ready pod -l app=redis -n fintech --timeout=120s
kubectl wait --for=condition=Ready pod -l app=kafka -n fintech --timeout=180s
```

### 4. Initialize Database

```bash
kubectl exec -n fintech -it postgres-0 -- psql -U postgres \
  -c "CREATE DATABASE financial_data;"
kubectl exec -n fintech -it postgres-0 -- psql -U postgres financial_data \
  -f /scripts/init-postgres.sql
```

### 5. Initialize Kafka Topics

```bash
kubectl exec -n fintech kafka-0 -- \
  kafka-topics --bootstrap-server localhost:9092 \
  --create --topic raw-ticks --partitions 32 --replication-factor 1
```

### 6. Deploy Application Services

```bash
kubectl apply -f k8s/deployments/data-ingestion.yaml
kubectl apply -f k8s/deployments/stream-processing.yaml
kubectl apply -f k8s/deployments/feature-store.yaml
kubectl apply -f k8s/deployments/ml-inference.yaml
kubectl apply -f k8s/deployments/api-gateway.yaml
```

### 7. Verify Deployment

```bash
kubectl get pods -n fintech
kubectl get svc -n fintech
kubectl logs -n fintech -l app=api-gateway --tail=100
```

### 8. Configure Ingress

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: api-gateway-ingress
  namespace: fintech
  annotations:
    nginx.ingress.kubernetes.io/rate-limit: "1000"
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
spec:
  rules:
    - host: api.fintech-platform.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: api-gateway
                port:
                  number: 8080
  tls:
    - hosts:
        - api.fintech-platform.com
      secretName: fintech-tls-cert
```

## Scaling Configuration

### ML Inference (GPU-Enabled)
```yaml
resources:
  limits:
    nvidia.com/gpu: 1
    memory: 8Gi
```

### Kafka Partition Scaling
```bash
kafka-topics --bootstrap-server kafka:9092 \
  --alter --topic raw-ticks --partitions 64
```

### HPA for All Services
```bash
kubectl autoscale deployment api-gateway -n fintech \
  --min=3 --max=20 --cpu-percent=70

kubectl autoscale deployment ml-inference -n fintech \
  --min=3 --max=10 --cpu-percent=70
```

## Monitoring Setup

### Prometheus Operator
```bash
helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring --create-namespace \
  --set prometheus.prometheusSpec.retention=30d
```

### Grafana Datasources (auto-provisioned)
Dashboards are auto-provisioned from `docker/grafana/dashboards/`.

## Backup Strategy

### PostgreSQL Backup
```bash
kubectl exec -n fintech postgres-0 -- \
  pg_dump -U postgres financial_data | gzip > backup_$(date +%Y%m%d).sql.gz
```

### Redis Backup
```bash
kubectl exec -n fintech redis-0 -- redis-cli -a "$REDIS_PASSWORD" SAVE
```

## Disaster Recovery

1. Stop all traffic at ingress
2. Scale down deployments to 0
3. Restore PostgreSQL from latest backup
4. Restore Redis from latest backup
5. Scale deployments back up
6. Verify with health checks

## Security Hardening

- Enable TLS 1.3 on all external endpoints
- Configure NetworkPolicies to restrict pod-to-pod communication
- Enable audit logging on Kubernetes API server
- Rotate secrets every 90 days
- Use Vault or Azure Key Vault for secret management