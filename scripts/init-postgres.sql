-- Create database if not exists
SELECT 'CREATE DATABASE financial_data'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'financial_data')\gexec

\c financial_data

-- Features table (partitioned by timestamp)
CREATE TABLE IF NOT EXISTS features (
    id BIGSERIAL,
    symbol VARCHAR(20) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    rsi14 DECIMAL(10,4),
    macd DECIMAL(12,4),
    macd_signal DECIMAL(12,4),
    macd_histogram DECIMAL(12,4),
    volatility_std DECIMAL(12,8),
    bb_width DECIMAL(12,8),
    bb_upper DECIMAL(12,4),
    bb_lower DECIMAL(12,4),
    volume_ma_ratio DECIMAL(10,4),
    price_momentum DECIMAL(12,8),
    rate_of_change DECIMAL(12,4),
    atr14 DECIMAL(12,4),
    bid_ask_spread DECIMAL(12,8),
    price DECIMAL(20,8),
    volume DECIMAL(20,8),
    hour_of_day INTEGER,
    day_of_week INTEGER,
    exchange VARCHAR(20),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (id, timestamp)
) PARTITION BY RANGE (timestamp);

CREATE INDEX IF NOT EXISTS idx_features_symbol_timestamp ON features (symbol, timestamp DESC);

-- Predictions table
CREATE TABLE IF NOT EXISTS predictions (
    id BIGSERIAL,
    symbol VARCHAR(20) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    prediction INTEGER,
    confidence DECIMAL(6,4),
    probability_up DECIMAL(6,4),
    probability_down DECIMAL(6,4),
    model_version VARCHAR(50),
    latest_price DECIMAL(20,8),
    feature_timestamp TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (id, timestamp)
) PARTITION BY RANGE (timestamp);

CREATE INDEX IF NOT EXISTS idx_predictions_symbol_timestamp ON predictions (symbol, timestamp DESC);

-- Model metadata table
CREATE TABLE IF NOT EXISTS model_metadata (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    model_version VARCHAR(50) NOT NULL UNIQUE,
    model_type VARCHAR(20),
    feature_count INTEGER,
    trained_at TIMESTAMPTZ,
    last_retraining_at TIMESTAMPTZ,
    status VARCHAR(20),
    accuracy DECIMAL(6,4),
    auc DECIMAL(6,4)
);

-- Backtest results table
CREATE TABLE IF NOT EXISTS backtest_results (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    period VARCHAR(50),
    total_return DECIMAL(10,4),
    sharpe_ratio DECIMAL(8,4),
    max_drawdown DECIMAL(10,4),
    win_rate DECIMAL(6,4),
    sample_size INTEGER,
    profit_factor DECIMAL(8,4),
    computed_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_backtest_symbol ON backtest_results (symbol, computed_at DESC);