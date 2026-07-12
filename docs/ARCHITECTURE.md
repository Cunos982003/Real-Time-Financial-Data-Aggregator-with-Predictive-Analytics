# Architecture Deep Dive

## System Overview

The Financial Data Aggregator is a microservice-based platform designed for real-time financial market data ingestion, feature computation, ML-based price movement prediction, and low-latency API serving.

## Services Architecture

### 1. Data Ingestion Service (Port 8081)

Responsible for connecting to external exchanges and publishing raw tick data to Kafka.

**Components:**
- `ExchangeConnectorFactory` — manages all exchange connectors
- `BinanceConnector`, `CoinbaseConnector`, `KrakenConnector`, `YahooFinanceConnector` — WebSocket/REST clients per exchange
- `KafkaProducerConfig` — idempotent Kafka producer with acks=all
- `HealthController` — actuator health endpoint

**Data Flow:**
```
Exchange WebSocket/REST → Connector → TickMessage → Kafka Topic (raw-ticks)
```

**Error Recovery:**
- Exponential backoff retry (5s initial, up to 10 attempts)
- Per-symbol reconnection logic
- Connection state tracking per exchange

### 2. Stream Processing Service (Port 8082)

Apache Kafka Streams for feature computation.

**Components:**
- `StreamTopology` — Kafka Streams topology builder
- `TickAggregationProcessor` — 1-minute OHLCV candle aggregation
- `FeatureEngineeringProcessor` — technical indicator computation
- `KafkaStreamsConfig` — exactly-once processing guarantee

**Kafka Streams Topology:**
```
raw-ticks → TickAggregationProcessor → (candles topic)
                              ↓
                     FeatureEngineeringProcessor → (features topic)
```

### 3. Feature Store Service (Port 8084)

Hot/cold storage layer combining Redis and PostgreSQL.

**Storage Tiers:**
- **L1 (Redis)**: Latest feature vector per symbol, 24h TTL, event-based invalidation
- **L2 (PostgreSQL)**: Full feature history, 90-day retention, partitioned by timestamp

**Key Classes:**
- `RedisFeatureStore` — Redis template wrapper with hot cache operations
- `TimeSeriesFeatureStore` — JPA repository for time-series features
- `FeatureStoreService` — unified service with cache-through pattern

### 4. ML Inference Service (Port 8083)

Machine learning model training, serving, and drift detection.

**Model Architecture:**
- MLP neural network (15 input features → 32 hidden → 2 output)
- Features: RSI(14), MACD, Bollinger Bands, ATR(14), Volume MA Ratio, etc.
- Online learning: incremental model updates from recent data
- Confidence scoring via softmax probability output

**Drift Detection:**
- KL Divergence between baseline and current feature distributions
- PSI (Population Stability Index) monitoring
- Auto-retraining triggered at threshold 0.1

**API Endpoints:**
- `POST /api/v1/models/{symbol}/train` — manual training
- `GET /api/v1/predictions/{symbol}` — latest prediction
- `GET /api/v1/backtest/{symbol}` — backtest strategy metrics

### 5. API Gateway (Port 8080)

Orchestration layer with JWT security and WebSocket support.

**Security:**
- JWT token validation via `JwtTokenProvider`
- API rate limiting: 1000 req/min per token
- Role-based access control (RBAC)

### 6. Monitoring Service (Port 8085)

Prometheus metrics collection, alerting, and system health.

**Metric Categories:**
- Prediction latency (P50/P95/P99)
- Model accuracy drift
- Kafka consumer lag
- JVM memory/GC metrics
- HTTP request rate

## Data Flow Summary

```
Exchange → Ingestion → Kafka(raw-ticks) → Stream Processing → Kafka(features)
                                                          ↓
                    PostgreSQL ← Feature Store ← ML Inference → Redis
                                         ↓
                API Gateway ← Backtest ← WebSocket Stream → Dashboard
```

## Technology Decisions

### Why a Microservices Approach?
- Independent scaling: ML inference can scale separately from ingestion
- Fault isolation: Kafka failure doesn't affect API gateway
- Technology diversity: each service uses the optimal stack for its workload
- Team ownership: clear boundaries for deployment and development

### Why Kafka over other messaging?
- Ordered partitions for per-symbol ordering guarantees
- Exactly-once semantics for prediction consistency
- Topic-based routing for multiple consumers
- Offset management for replay and backfill

### Why Redis + PostgreSQL instead of just TimescaleDB?
- Sub-millisecond latency for hot feature access in Redis
- PostgreSQL handles complex analytical queries
- Clear separation of read/write patterns
- Redis provides natural L1 cache structure

## Scalability Considerations

| Component | Scaling Strategy |
|-----------|-----------------|
| data-ingestion | Horizontal (per exchange) |
| stream-processing | Horizontal (Kafka consumer groups) |
| feature-store | Redis cluster, read replicas |
| ml-inference | Horizontal (stateless inference) |
| api-gateway | Horizontal + HPA CPU/memory based |