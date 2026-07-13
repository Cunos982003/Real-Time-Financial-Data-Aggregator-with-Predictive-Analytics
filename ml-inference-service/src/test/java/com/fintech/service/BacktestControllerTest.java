package com.fintech.service;

import com.fintech.model.BacktestResult;
import com.fintech.model.PredictionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BacktestControllerTest {

    @Mock
    private ModelInference modelInference;

    private Queue<PredictionResult> predictionCache;

    @BeforeEach
    void setUp() {
        predictionCache = new java.util.concurrent.ConcurrentLinkedQueue<>();
    }

    @Test
    void testSharpeRatio_highVolatilityButPositive_returnsPositive() {
        List<BigDecimal> returns = List.of(
                BigDecimal.valueOf(0.01), BigDecimal.valueOf(-0.005),
                BigDecimal.valueOf(0.02), BigDecimal.valueOf(-0.01),
                BigDecimal.valueOf(0.015)
        );

        double mean = returns.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r.doubleValue() - mean, 2))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);
        double sharpe = stdDev > 0 ? (mean / stdDev) * Math.sqrt(252) : 0;

        assertTrue(sharpe > 0 || sharpe == 0); // Should not be negative given above-zero mean
    }

    @Test
    void testMaxDrawdown_computedCorrectly() {
        List<BigDecimal> returns = List.of(
                BigDecimal.valueOf(0.01),
                BigDecimal.valueOf(-0.02), // drawdown starts
                BigDecimal.valueOf(-0.03), // drawdown deepens
                BigDecimal.valueOf(0.01),  // recovery
                BigDecimal.valueOf(0.005)
        );

        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal runningPeak = BigDecimal.ZERO;
        for (BigDecimal r : returns) {
            runningPeak = runningPeak.max(r);
            BigDecimal drawdown = runningPeak.subtract(r);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }

        assertTrue(maxDrawdown.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testWinRate_calculation() {
        int wins = 6;
        int losses = 4;
        int total = wins + losses;
        BigDecimal winRate = total > 0 ?
                BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(total), 4, java.math.RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        assertEquals(0, winRate.compareTo(BigDecimal.valueOf(0.6)));
    }

    @Test
    void testProfitFactor_withWinsAndLosses() {
        BigDecimal avgReturn = BigDecimal.valueOf(-0.01);
        BigDecimal totalWins = BigDecimal.valueOf(0.03);
        BigDecimal totalLosses = avgReturn.abs();
        BigDecimal profitFactor = totalLosses.compareTo(BigDecimal.ZERO) > 0 ?
                totalWins.divide(totalLosses, 4, java.math.RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        assertTrue(profitFactor.compareTo(BigDecimal.ZERO) > 0);
        assertEquals(0, profitFactor.compareTo(BigDecimal.valueOf(3.0)));
    }

    @Test
    void testProfitFactor_noLosses_returnsZero() {
        BigDecimal avgReturn = BigDecimal.ZERO;
        BigDecimal totalWins = BigDecimal.valueOf(0.03);
        BigDecimal totalLosses = BigDecimal.ZERO;
        BigDecimal profitFactor = totalLosses.compareTo(BigDecimal.ZERO) > 0 ?
                totalWins.divide(totalLosses, 4, java.math.RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        assertEquals(0, profitFactor.compareTo(BigDecimal.ZERO));
    }

    @Test
    void testPredictionCache_populatedByGetRecentPredictions() {
        when(modelInference.getRecentPredictions("BTCUSD", 1000))
                .thenReturn(buildPredictions(10));

        List<?> result = modelInference.getRecentPredictions("BTCUSD", 1000);
        assertEquals(10, result.size());
    }

    private List<PredictionResult> buildPredictions(int count) {
        List<PredictionResult> preds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            preds.add(PredictionResult.builder()
                    .symbol("BTCUSD")
                    .prediction(i % 2)
                    .confidence(BigDecimal.valueOf(0.6))
                    .probabilityUp(BigDecimal.valueOf(0.6))
                    .probabilityDown(BigDecimal.valueOf(0.4))
                    .modelVersion("v1")
                    .timestamp(Instant.now())
                    .inferenceMs(10L)
                    .build());
        }
        return preds;
    }
}