package com.fintech.service;

import com.fintech.model.ModelMetadata;
import com.fintech.model.TrainingData;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoRetrainingService {

    private final ModelTrainer modelTrainer;
    private final DriftDetector driftDetector;
    private final MeterRegistry meterRegistry;

    private static final List<String> TRACKED_SYMBOLS = List.of("BTCUSD", "ETHUSD", "XRPUSD");
    private static final List<Double> REFERENCE_RSI = List.of(50.0, 45.0, 55.0, 40.0, 60.0);
    private static final List<Double> REFERENCE_MACD = List.of(0.0, -10.0, 10.0, -5.0, 5.0);
    private static final List<Double> REFERENCE_PRICE = List.of(42000.0, 43000.0, 41000.0, 44000.0, 40000.0);

    @Scheduled(cron = "0 0 2 * * *")
    public void scheduledRetraining() {
        log.info("Starting scheduled retraining for all symbols");
        for (String symbol : TRACKED_SYMBOLS) {
            try {
                retrainAndUpdateBaseline(symbol, REFERENCE_PRICE);
            } catch (Exception e) {
                log.error("Scheduled retraining failed for {}: {}", symbol, e.getMessage());
            }
        }
    }

    private void retrainAndUpdateBaseline(String symbol, List<Double> refValues) {
        long startMs = System.currentTimeMillis();

        TrainingData synthetic = buildSyntheticTrainingData(symbol, 60);
        ModelMetadata result = modelTrainer.train(symbol, synthetic);
        long durationMs = System.currentTimeMillis() - startMs;

        driftDetector.setBaseline(symbol + "_price", refValues);
        driftDetector.setBaseline(symbol + "_rsi", REFERENCE_RSI);
        driftDetector.setBaseline(symbol + "_macd", REFERENCE_MACD);

        meterRegistry.timer("model_retraining_duration_seconds", "symbol", symbol)
                .record(durationMs, TimeUnit.MILLISECONDS);
        log.info("Auto-retraining complete for {} in {}ms", symbol, durationMs);
    }

    private TrainingData buildSyntheticTrainingData(String symbol, int points) {
        java.util.Random rand = new java.util.Random(symbol.hashCode());
        java.util.List<double[]> features = new java.util.ArrayList<>();
        java.util.List<Integer> labels = new java.util.ArrayList<>();

        double basePrice = symbol.contains("BTC") ? 42000 : symbol.contains("ETH") ? 2500 : 0.6;
        double price = basePrice;
        for (int i = 0; i < points; i++) {
            price += (rand.nextDouble() - 0.48) * basePrice * 0.01;
            double rsi = 30 + rand.nextDouble() * 40;
            double macd = (rand.nextDouble() - 0.5) * 10;
            double atr = rand.nextDouble() * 100;
            double volumeRatio = 0.8 + rand.nextDouble() * 0.4;

            double[] featureVector = new double[15];
            featureVector[0] = rsi;
            featureVector[1] = macd;
            featureVector[2] = macd * 0.9;
            featureVector[3] = macd * 0.1;
            featureVector[4] = rand.nextDouble() * 0.02;
            featureVector[5] = rand.nextDouble() * 0.05;
            featureVector[6] = rand.nextDouble() * 0.05;
            featureVector[7] = volumeRatio;
            featureVector[8] = (rand.nextDouble() - 0.5) * 0.02;
            featureVector[9] = rand.nextDouble() * 5;
            featureVector[10] = atr;
            featureVector[11] = 0.0002 + rand.nextDouble() * 0.0003;
            featureVector[12] = price;
            featureVector[13] = (price * volumeRatio) / 1e6;
            featureVector[14] = rand.nextInt(24);

            features.add(featureVector);
            labels.add(rand.nextDouble() > 0.5 ? 1 : 0);
        }

        return TrainingData.builder()
                .symbol(symbol)
                .features(features)
                .labels(labels)
                .build();
    }
}