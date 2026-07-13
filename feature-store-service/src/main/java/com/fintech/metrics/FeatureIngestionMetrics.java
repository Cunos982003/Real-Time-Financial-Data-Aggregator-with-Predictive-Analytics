package com.fintech.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class FeatureIngestionMetrics {

    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicLong> latestFeatureTimestamp = new ConcurrentHashMap<>();

    public FeatureIngestionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordFeatureTimestamp(String symbol, Instant timestamp) {
        latestFeatureTimestamp.computeIfAbsent(symbol, s -> {
            AtomicLong holder = new AtomicLong(System.currentTimeMillis());
            Gauge.builder("feature_ingestion_lag_seconds", holder, AtomicLong::get)
                    .tag("symbol", s)
                    .description("Time since last feature was ingested for this symbol")
                    .register(meterRegistry);
            return holder;
        }).set(timestamp.toEpochMilli());
    }

    public double getCurrentLagSeconds(String symbol) {
        AtomicLong stored = latestFeatureTimestamp.get(symbol);
        if (stored == null) return -1;
        return Duration.between(
                Instant.ofEpochMilli(stored.get()),
                Instant.now()
        ).toMillis() / 1000.0;
    }
}