package com.fintech.service;

import com.fintech.model.DriftResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DriftDetector {

    private static final double DEFAULT_THRESHOLD = 0.1;

    private final Map<String, double[]> baselineDistributions = new ConcurrentHashMap<>();
    private final Map<String, DriftResult> lastResults = new ConcurrentHashMap<>();

    public void setBaseline(String symbol, List<Double> referenceValues) {
        if (referenceValues == null || referenceValues.isEmpty()) return;
        double mean = referenceValues.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = referenceValues.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average().orElse(1e-6);
        baselineDistributions.put(symbol + "_mean", new double[]{mean});
        baselineDistributions.put(symbol + "_var", new double[]{variance});
    }

    public DriftResult detect(String symbol, List<Double> currentValues) {
        double threshold = DEFAULT_THRESHOLD;
        double baselineMean = baselineDistributions.getOrDefault(symbol + "_mean", new double[]{50.0})[0];
        double baselineVar = baselineDistributions.getOrDefault(symbol + "_var", new double[]{1.0})[0];

        if (currentValues == null || currentValues.isEmpty()) {
            return buildResult(symbol, 0, 0, false, threshold);
        }

        double currentMean = currentValues.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double currentVar = currentValues.stream()
                .mapToDouble(v -> Math.pow(v - currentMean, 2))
                .average().orElse(1e-6);

        double klDiv = computeKLDivergence(baselineMean, baselineVar, currentMean, currentVar);
        double psi = computePSI(baselineMean, baselineVar, currentMean, currentVar);
        boolean drift = psi > threshold || klDiv > threshold * 10;

        String status = drift ? "DRIFT_DETECTED" : "NORMAL";
        if (drift) {
            log.warn("Drift detected for {}: KL={}, PSI={}", symbol, klDiv, psi);
        }

        DriftResult result = buildResult(symbol, klDiv, psi, drift, threshold);
        lastResults.put(symbol, result);
        return result;
    }

    public DriftResult getLastResult(String symbol) {
        return lastResults.get(symbol);
    }

    private double computeKLDivergence(double mu0, double sigma0, double mu1, double sigma1) {
        double var0 = Math.max(sigma0, 1e-6);
        double var1 = Math.max(sigma1, 1e-6);
        return 0.5 * (Math.log(var1 / var0) + (var0 + Math.pow(mu0 - mu1, 2)) / var1 - 1);
    }

    private double computePSI(double mu0, double sigma0, double mu1, double sigma1) {
        double var0 = Math.max(sigma0, 1e-6);
        double var1 = Math.max(sigma1, 1e-6);
        double ratio0 = mu0 / Math.sqrt(var0);
        double ratio1 = mu1 / Math.sqrt(var1);
        return Math.abs(ratio1 - ratio0) / (ratio0 > 0 ? ratio0 : 1);
    }

    private DriftResult buildResult(String symbol, double kl, double psi, boolean drift, double threshold) {
        return DriftResult.builder()
                .symbol(symbol)
                .klDivergence(BigDecimal.valueOf(kl).doubleValue())
                .psi(BigDecimal.valueOf(psi).doubleValue())
                .driftDetected(drift)
                .threshold(threshold)
                .status(drift ? "DRIFT_DETECTED" : "NORMAL")
                .build();
    }
}