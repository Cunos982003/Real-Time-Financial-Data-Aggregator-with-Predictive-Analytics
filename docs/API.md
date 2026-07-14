# API Documentation

## Base URL
```
Production: http://api-gateway:8080
Local:      http://localhost:8080
```

## Authentication

Most endpoints are currently open (permissive for development). Production deployments should use JWT authentication:

```
Authorization: Bearer <jwt_token>
```

## Endpoints

### GET /api/v1/predictions/{symbol}

Get the latest price movement prediction for a symbol.

**Request:**
```http
GET /api/v1/predictions/BTCUSD HTTP/1.1
```

**Response:**
```json
{
  "symbol": "BTCUSD",
  "prediction": 1,
  "confidence": 0.7823,
  "probabilityUp": 0.7823,
  "probabilityDown": 0.2177,
  "modelVersion": "2024-01-15T10:30:00Z",
  "latestPrice": 42150.50,
  "timestamp": "2024-01-15T10:35:22Z",
  "inferenceMs": 4
}
```

**Error Responses:**
- `404`: Symbol not found
- `503`: ML service unavailable

---

### GET /api/v1/predictions/{symbol}/history

Get recent prediction history.

**Request:**
```http
GET /api/v1/predictions/BTCUSD/history?count=100 HTTP/1.1
```

**Response:**
```json
[
  {
    "symbol": "BTCUSD",
    "prediction": 1,
    "confidence": 0.7823,
    "probabilityUp": 0.7823,
    "probabilityDown": 0.2177,
    "timestamp": "2024-01-15T10:35:22Z",
    "inferenceMs": 4
  }
]
```

---

### POST /api/v1/models/{symbol}/train

Trigger model training for a symbol.

**Request:**
```http
POST /api/v1/models/BTCUSD/train HTTP/1.1
Content-Type: application/json

{
  "lookbackDays": 30,
  "testSplit": 0.2,
  "modelType": "mlp",
  "symbol": "BTCUSD"
}
```

**Response:**
```json
{
  "symbol": "BTCUSD",
  "modelVersion": "2024-01-15T12:00:00Z",
  "modelType": "mlp",
  "trainedAt": "2024-01-15T12:00:00Z",
  "lastRetrainingAt": "2024-01-15T12:00:00Z",
  "featureCount": 15,
  "trainSamples": 43200,
  "testSamples": 10800,
  "status": "READY"
}
```

---

### GET /api/v1/models/{symbol}/metrics

Get model performance metrics.

**Request:**
```http
GET /api/v1/models/BTCUSD/metrics HTTP/1.1
```

**Response:**
```json
{
  "symbol": "BTCUSD",
  "modelVersion": "2024-01-15T10:30:00Z",
  "trainingMetrics": {
    "accuracy": 0.654,
    "auc": 0.721,
    "precision": 0.68,
    "recall": 0.62
  },
  "driftStatus": "NORMAL",
  "driftScore": 0.02,
  "lastRetrainingDate": "2024-01-14T02:00:00Z",
  "nextRetrainingDate": "2024-01-15T02:00:00Z"
}
```

---

### GET /api/v1/backtest/{symbol}

Run backtest on historical predictions.

**Request:**
```http
GET /api/v1/backtest/BTCUSD?days=30&predictionHorizon=60 HTTP/1.1
```

**Response:**
```json
{
  "symbol": "BTCUSD",
  "period": "30 days",
  "metrics": {
    "totalReturn": 0.052,
    "sharpeRatio": 1.85,
    "maxDrawdown": -0.12,
    "winRate": 0.58,
    "sampleSize": 4320,
    "profitFactor": 1.45
  },
  "computedAt": "2024-01-15T10:40:00Z"
}
```

---

### GET /api/v1/features/{symbol}

Get feature window data.

**Request:**
```http
GET /api/v1/features/BTCUSD?lookbackSeconds=3600 HTTP/1.1
```

**Response:**
```json
[
  {
    "timestamp": "2024-01-15T09:35:00Z",
    "rsi14": 65.2,
    "macd": 1.5,
    "macdSignal": 0.8,
    "macdHistogram": 0.7,
    "volatilityStd": 0.0125,
    "bbWidth": 0.0845,
    "volumeMaRatio": 1.23,
    "priceMomentum": 0.0032,
    "rateOfChange": 2.1,
    "atr14": 85.5,
    "bidAskSpread": 0.001,
    "price": 42150.50,
    "volume": 125.5,
    "hourOfDay": 9,
    "dayOfWeek": 1
  }
]
```

---

### SSE GET /ws/stream/{symbol}

Real-time prediction streaming via Server-Sent Events. No authentication required. Streams predictions by polling ml-inference every `intervalMs` (default: 5000ms).

**Connection:**
```http
GET http://localhost:8080/ws/stream/BTCUSD
GET http://localhost:8080/ws/stream/BTCUSD?intervalMs=10000
```

**SSE Event Format:**
```
event: prediction
data: {"symbol":"BTCUSD","prediction":1,"confidence":0.7823,"probabilityUp":0.7823,"probabilityDown":0.2177,"latestPrice":42150.50,"timestamp":"2024-01-15T10:35:23Z","inferenceMs":4,"modelVersion":"..."}
id: BTCUSD
```

**Curl test:**
```bash
curl -N http://localhost:8080/ws/stream/BTCUSD
```

---

### Health Check

```http
GET /actuator/health HTTP/1.1
```

Response:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

---

## Rate Limits

| Tier | Limit |
|------|-------|
| Default | 1000 req/min per token |
| Burst | 100 req/sec |

## Error Format

```json
{
  "timestamp": "2024-01-15T10:35:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid symbol format",
  "path": "/api/v1/predictions/INVALID"
}
```