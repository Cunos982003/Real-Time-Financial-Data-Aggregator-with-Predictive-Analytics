package com.fintech.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class PredictionMetrics {

    private final MeterRegistry meterRegistry;
    private final Map<String, Timer> predictionTimers = new ConcurrentHashMap<>();
    private final Counter predictionRequestsTotal;

    public PredictionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.predictionRequestsTotal = Counter.builder("prediction_requests_total")
                .description("Total number of prediction requests")
                .register(meterRegistry);
    }

    public void recordPredictionRequest(String symbol) {
        predictionRequestsTotal.increment();
        Counter.builder("prediction_requests")
                .tag("symbol", symbol)
                .register(meterRegistry)
                .increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordPredictionLatency(Timer.Sample sample, String symbol) {
        sample.stop(Timer.builder("prediction_latency_seconds")
                .tag("symbol", symbol)
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry));
    }

    public void recordInferenceQueueDepth(int depth, String symbol) {
        Gauge.builder("inference_queue_depth", () -> depth)
                .tag("symbol", symbol)
                .register(meterRegistry);
    }

    public void recordModelAccuracy(double accuracy, String symbol) {
        Gauge.builder("model_accuracy", () -> accuracy)
                .tag("symbol", symbol)
                .register(meterRegistry);
    }

    public void recordModelDrift(double driftScore, String symbol) {
        Gauge.builder("model_drift_score", () -> driftScore)
                .tag("symbol", symbol)
                .register(meterRegistry);
    }

    public void recordPrediction(int prediction, double confidence, String symbol) {
        Gauge.builder("prediction_confidence", () -> confidence)
                .tag("symbol", symbol)
                .tag("prediction", String.valueOf(prediction))
                .register(meterRegistry);
    }

    public void recordRetrainingDuration(long durationMs, String symbol) {
        Timer.builder("model_retraining_duration_seconds")
                .tag("symbol", symbol)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }
}