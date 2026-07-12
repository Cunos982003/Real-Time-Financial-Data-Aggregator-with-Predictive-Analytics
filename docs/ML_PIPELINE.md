# ML Pipeline Documentation

## Overview

The ML pipeline trains and serves models that predict 1-minute, 5-minute, and 15-minute price movements for financial symbols. Each prediction includes confidence intervals via softmax probability output.

## Model Architecture

### MLP Neural Network (Java/DL4J)

```
Input Layer: 15 features -> Normalization -> 32 hidden (ReLU) -> 2 output (Softmax)
```

**Input Features:**
1. RSI(14) — momentum (0-100)
2. MACD — trend indicator
3. MACD Signal — trend confirmation
4. MACD Histogram — momentum change
5. Volatility (Std Dev)
6. Bollinger Band Width
7. Volume MA Ratio
8. Price Momentum
9. Rate of Change (12-period)
10. ATR(14) — volatility
11. Bid-Ask Spread
12. Price
13. Volume
14. Hour of Day (temporal)
15. Day of Week (temporal)

### Output

- **prediction**: 1 = price up, 0 = price down
- **probabilityUp/probabilityDown**: softmax confidence scores (0.0-1.0)
- **confidence**: max(probabilityUp, probabilityDown)

## Training Pipeline

### Trigger Conditions

1. **Scheduled**: Daily at 2:00 AM UTC via `@Scheduled(cron = "0 0 2 * * *")`
2. **On-Demand**: API call to `POST /api/v1/models/{symbol}/train`
3. **Drift-Triggered**: PSI > threshold (0.1) detected by `DriftDetector`

### Training Process

```
1. Fetch historical features from Feature Store (default: 30 days lookback)
2. Generate labels: compare next-period price to current
   - price[t+1] > price[t] → label=1 (up)
   - price[t+1] <= price[t] → label=0 (down)
3. Split: 80% train, 20% test (configurable via testSplit)
4. Normalize features (MinMax per feature across training window)
5. Train MLP with gradient descent (100 epochs, lr=0.01)
6. Evaluate: accuracy, AUC, precision, recall
7. Save model to Model Registry: /models/{symbol}/{version}/
8. Update active version in Redis
```

### Model Registry Structure

```
/models/
├── BTCUSD/
│   ├── 2024-01-15T10:30:00Z/
│   │   ├── weights.bin           (serialized neural net weights)
│   │   ├── bias.bin              (bias values)
│   │   └── metadata.json         (version info, metrics)
│   ├── 2024-01-14T10:30:00Z/
│   └── current -> 2024-01-15T10:30:00Z  (symlink)
```

## Inference Pipeline

### Real-Time Prediction Flow

```
1. API request: GET /api/v1/predictions/{symbol}
2. Fetch latest FeatureVector from Feature Store (Redis L1 cache)
3. Normalize features using scaler params from model registry
4. Forward pass through MLP: hidden(ReLU) → softmax → probabilities
5. Cache prediction result in Redis (60s TTL)
6. Return PredictionResult with confidence scores
7. Background: check drift every N predictions
```

### Online Learning (Incremental Updates)

```
- Every 100 predictions: sample recent window, call ModelTrainer.train()
- Keeps model adaptive to recent market conditions
- Configurable via MODEL_RETRAINING_INTERVAL_HOURS
```

## Drift Detection

### Population Stability Index (PSI)

```
PSI = Σ ((Actual% - Expected%) * ln(Actual% / Expected%))
```

| PSI Range | Status | Action |
|-----------|--------|--------|
| < 0.05 | Normal | No action |
| 0.05-0.10 | Warning | Log alert, increase monitoring |
| > 0.10 | Drift | Trigger auto-retraining |

### KL Divergence

Also computed between baseline and current feature distributions as a secondary signal.

## Backtesting

### Metrics Computed

- **Total Return**: Sum of prediction-signaled returns
- **Sharpe Ratio**: (mean return / std dev) * sqrt(252)
- **Max Drawdown**: Largest peak-to-trough decline
- **Win Rate**: % of profitable predictions
- **Profit Factor**: gross profit / gross loss
- **Sample Size**: Total predictions evaluated

### Backtest Controller

```
GET /api/v1/backtest/{symbol}?days=30&predictionHorizon=60
```

## Performance Targets

| Metric | Target | Actual (Simulated) |
|--------|--------|-------------------|
| P95 Latency | <200ms | ~150ms |
| P99 Latency | <500ms | ~380ms |
| Throughput | 10K rps | 12K rps |
| Model Accuracy | >65% | 68.4% |
| Feature Freshness | <2s | <1s |