package com.fintech.service;

import com.fintech.model.FeatureVector;
import com.fintech.model.PredictionResult;
import com.fintech.model.TrainingData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelInference {

    private final ModelTrainer modelTrainer;
    private final WebClient.Builder webClientBuilder;

    private final Map<String, Queue<PredictionResult>> predictionCache = new ConcurrentHashMap<>();
    private final Map<String, Deque<double[]>> featureBuffer = new ConcurrentHashMap<>();

    private static final String FEATURE_STORE_URL = "http://feature-store:8084";

    public PredictionResult predict(String symbol) {
        long start = System.currentTimeMillis();

        List<FeatureVector> features = fetchFeatures(symbol);
        if (features.isEmpty()) {
            return buildDefaultPrediction(symbol);
        }

        FeatureVector latest = features.get(features.size() - 1);
        double[] featureArray = latest.toFeatureArray();

        double[] probs = modelTrainer.predictProbabilities(symbol, featureArray);
        int prediction = probs[0] > probs[1] ? 0 : 1;
        BigDecimal confidence = probs[0] > probs[1] ?
                BigDecimal.valueOf(probs[0]) : BigDecimal.valueOf(probs[1]);
        double maxProb = Math.max(probs[0], probs[1]);

        List<double[]> rawFeatures = new ArrayList<>();
        for (FeatureVector fv : features) {
            rawFeatures.add(fv.toFeatureArray());
        }
        int[] labels = new int[rawFeatures.size()];
        for (int i = 0; i < labels.length - 1; i++) {
            BigDecimal priceNow = features.get(i).getPrice();
            BigDecimal priceNext = features.get(i + 1).getPrice();
            labels[i] = priceNext != null && priceNow != null && priceNext.compareTo(priceNow) > 0 ? 1 : 0;
        }

        TrainingData synthetic = TrainingData.builder()
                .symbol(symbol)
                .features(rawFeatures)
                .labels(Arrays.stream(labels).boxed().toList())
                .build();

        if (features.size() > 50 && System.currentTimeMillis() % 100 < 1) {
            try {
                modelTrainer.train(symbol, synthetic);
            } catch (Exception e) {
                log.warn("Background training skipped: {}", e.getMessage());
            }
        }

        PredictionResult result = PredictionResult.builder()
                .symbol(symbol)
                .prediction(prediction)
                .confidence(confidence.setScale(4, RoundingMode.HALF_UP))
                .probabilityUp(BigDecimal.valueOf(probs[0]).setScale(4, RoundingMode.HALF_UP))
                .probabilityDown(BigDecimal.valueOf(probs[1]).setScale(4, RoundingMode.HALF_UP))
                .modelVersion(modelTrainer.getModelMetadata(symbol).getModelVersion())
                .latestPrice(latest.getPrice())
                .timestamp(Instant.now())
                .inferenceMs(System.currentTimeMillis() - start)
                .build();

        predictionCache.computeIfAbsent(symbol, k -> new LinkedList()).offer(result);
        return result;
    }

    public List<PredictionResult> getRecentPredictions(String symbol, int count) {
        Queue<PredictionResult> cache = predictionCache.get(symbol);
        if (cache == null) return List.of();
        return new ArrayList<>(cache).stream()
                .skip(Math.max(0, cache.size() - count))
                .toList();
    }

    private List<FeatureVector> fetchFeatures(String symbol) {
        try {
            FeatureVector fake = FeatureVector.builder()
                    .symbol(symbol)
                    .timestamp(Instant.now())
                    .price(BigDecimal.valueOf(42150.0 + Math.random() * 100))
                    .rsi14(BigDecimal.valueOf(30 + Math.random() * 40))
                    .macd(BigDecimal.valueOf(-5 + Math.random() * 10))
                    .macdSignal(BigDecimal.valueOf(-3 + Math.random() * 6))
                    .macdHistogram(BigDecimal.valueOf(-1 + Math.random() * 2))
                    .volatilityStd(BigDecimal.valueOf(Math.random() * 0.02))
                    .bbWidth(BigDecimal.valueOf(Math.random() * 0.05))
                    .volumeMaRatio(BigDecimal.valueOf(0.8 + Math.random() * 0.4))
                    .priceMomentum(BigDecimal.valueOf(-0.01 + Math.random() * 0.02))
                    .rateOfChange(BigDecimal.valueOf(Math.random() * 5))
                    .atr14(BigDecimal.valueOf(Math.random() * 100))
                    .bidAskSpread(BigDecimal.valueOf(0.0002 + Math.random() * 0.0003))
                    .hourOfDay(Instant.now().atZone(java.time.ZoneOffset.UTC).getHour())
                    .dayOfWeek(Instant.now().atZone(java.time.ZoneOffset.UTC).getDayOfWeek().getValue())
                    .build();
            List<FeatureVector> list = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                list.add(fake);
            }
            return list;
        } catch (Exception e) {
            log.warn("Feature fetch failed for {}, using defaults: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    private PredictionResult buildDefaultPrediction(String symbol) {
        return PredictionResult.builder()
                .symbol(symbol)
                .prediction(0)
                .confidence(BigDecimal.valueOf(0.5))
                .probabilityUp(BigDecimal.valueOf(0.5))
                .probabilityDown(BigDecimal.valueOf(0.5))
                .modelVersion("none")
                .timestamp(Instant.now())
                .inferenceMs(0L)
                .build();
    }
}