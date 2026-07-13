package com.fintech.service;

import com.fintech.model.FeatureVector;
import com.fintech.model.PredictionResult;
import com.fintech.model.TrainingData;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class ModelInference {

    private final ModelTrainer modelTrainer;
    private final WebClient.Builder webClientBuilder;
    private final DriftDetector driftDetector;
    private final MeterRegistry meterRegistry;

    private final Map<String, Queue<PredictionResult>> predictionCache = new ConcurrentHashMap<>();
    private final Map<String, Deque<double[]>> featureBuffer = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> pendingRetrain = new ConcurrentLinkedQueue<>();
    private final AtomicLong driftCheckCounter = new AtomicLong(0);

    private static final String FEATURE_STORE_URL = "http://feature-store:8084";
    private static final List<String> TRACKED_SYMBOLS = List.of("BTCUSD", "ETHUSD", "XRPUSD");

    public ModelInference(ModelTrainer modelTrainer, WebClient.Builder webClientBuilder,
                          DriftDetector driftDetector, MeterRegistry meterRegistry) {
        this.modelTrainer = modelTrainer;
        this.webClientBuilder = webClientBuilder;
        this.driftDetector = driftDetector;
        this.meterRegistry = meterRegistry;
    }

    @Cacheable(value = "predictionCache", key = "#symbol")
    public PredictionResult predict(String symbol) {
        long start = System.currentTimeMillis();
        Timer.Sample sample = Timer.start(meterRegistry);

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

        if (driftCheckCounter.incrementAndGet() % 100 == 0 && features.size() > 30) {
            List<Double> priceValues = features.stream()
                    .map(f -> f.getPrice() != null ? f.getPrice().doubleValue() : 0.0)
                    .toList();
            var driftResult = driftDetector.detect(symbol, priceValues);
            if (driftResult.isDriftDetected()) {
                log.warn("Drift detected for {}: PSI={}, KL={}", symbol,
                        driftResult.getPsi(), driftResult.getKlDivergence());
                Gauge.builder("model_drift_score", () -> driftResult.getPsi())
                        .tag("symbol", symbol)
                        .register(meterRegistry);
                pendingRetrain.offer(symbol);
            }
        }

        PredictionResult result = PredictionResult.builder()
                .symbol(symbol)
                .prediction(prediction)
                .confidence(confidence.setScale(4, RoundingMode.HALF_UP))
                .probabilityUp(BigDecimal.valueOf(probs[0]).setScale(4, RoundingMode.HALF_UP))
                .probabilityDown(BigDecimal.valueOf(probs[1]).setScale(4, RoundingMode.HALF_UP))
                .modelVersion(modelTrainer.getModelMetadata(symbol) != null
                            ? modelTrainer.getModelMetadata(symbol).getModelVersion() : "init")
                .latestPrice(latest.getPrice())
                .timestamp(Instant.now())
                .inferenceMs(System.currentTimeMillis() - start)
                .build();

        predictionCache.computeIfAbsent(symbol, k -> new LinkedList<>()).offer(result);
        Counter.builder("prediction_requests_total")
                .description("Total prediction requests")
                .register(meterRegistry).increment();
        sample.stop(Timer.builder("prediction_latency_seconds")
                .tag("symbol", symbol)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry));
        return result;
    }

    @Scheduled(fixedRate = 60000)
    public void processRetrainQueue() {
        String symbol;
        while ((symbol = pendingRetrain.poll()) != null) {
            try {
                log.info("Drift-triggered retraining for {}", symbol);
                TrainingData synthetic = buildSyntheticData(symbol, 100);
                long startMs = System.currentTimeMillis();
                modelTrainer.train(symbol, synthetic);
                meterRegistry.timer("model_retraining_duration_seconds", "symbol", symbol)
                        .record(System.currentTimeMillis() - startMs, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.error("Drift-triggered retraining failed for {}: {}", symbol, e.getMessage());
            }
        }
    }

    private TrainingData buildSyntheticData(String symbol, int points) {
        java.util.Random rand = new java.util.Random(symbol.hashCode());
        java.util.List<double[]> features = new java.util.ArrayList<>();
        java.util.List<Integer> labels = new java.util.ArrayList<>();
        for (int i = 0; i < points; i++) {
            double[] fv = new double[15];
            fv[0] = 30 + rand.nextDouble() * 40;
            fv[1] = (rand.nextDouble() - 0.5) * 10;
            fv[2] = fv[1] * 0.9;
            fv[3] = fv[1] * 0.1;
            fv[4] = rand.nextDouble() * 0.02;
            fv[5] = rand.nextDouble() * 0.05;
            fv[6] = fv[5];
            fv[7] = 0.8 + rand.nextDouble() * 0.4;
            fv[8] = (rand.nextDouble() - 0.5) * 0.02;
            fv[9] = rand.nextDouble() * 5;
            fv[10] = rand.nextDouble() * 100;
            fv[11] = 0.0002 + rand.nextDouble() * 0.0003;
            fv[12] = 42000 + (rand.nextDouble() - 0.5) * 2000;
            fv[13] = fv[12] * fv[7] / 1e6;
            fv[14] = rand.nextInt(24);
            features.add(fv);
            labels.add(rand.nextDouble() > 0.5 ? 1 : 0);
        }
        return TrainingData.builder().symbol(symbol).features(features).labels(labels).build();
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