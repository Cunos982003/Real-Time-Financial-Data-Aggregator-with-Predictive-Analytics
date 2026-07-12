package com.fintech.controller;

import com.fintech.model.BacktestResult;
import com.fintech.model.PredictionResult;
import com.fintech.service.ModelInference;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BacktestController {

    private final ModelInference modelInference;

    @GetMapping("/backtest/{symbol}")
    public ResponseEntity<BacktestResult> backtest(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "60") int predictionHorizon) {

        List<PredictionResult> history = modelInference.getRecentPredictions(symbol, 1000);
        List<BigDecimal> returns = new ArrayList<>();
        int wins = 0, losses = 0;

        for (PredictionResult pred : history) {
            if (pred.getProbabilityUp() != null) {
                BigDecimal ret = pred.getProbabilityUp().subtract(BigDecimal.valueOf(0.5));
                returns.add(ret);
                if (ret.compareTo(BigDecimal.ZERO) > 0) wins++;
                else losses++;
            }
        }

        if (returns.isEmpty()) {
            returns.add(BigDecimal.ZERO);
        }

        BigDecimal avgReturn = returns.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP);
        BigDecimal totalReturn = avgReturn.multiply(BigDecimal.valueOf(returns.size()));

        double mean = returns.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r.doubleValue() - mean, 2))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);
        double sharpe = stdDev > 0 ? (mean / stdDev) * Math.sqrt(252) : BigDecimal.ZERO.doubleValue();

        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal runningPeak = BigDecimal.ZERO;
        for (BigDecimal r : returns) {
            runningPeak = runningPeak.max(BigDecimal.ZERO.add(r));
            BigDecimal drawdown = runningPeak.subtract(r);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }

        int total = wins + losses;
        BigDecimal winRate = total > 0 ? BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        BigDecimal totalWins = avgReturn.compareTo(BigDecimal.ZERO) > 0 ? avgReturn : BigDecimal.ZERO;
        BigDecimal totalLosses = avgReturn.compareTo(BigDecimal.ZERO) < 0 ? avgReturn.abs() : BigDecimal.ZERO;
        BigDecimal profitFactor = totalLosses.compareTo(BigDecimal.ZERO) > 0 ?
                totalWins.divide(totalLosses, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        BacktestResult result = BacktestResult.builder()
                .symbol(symbol)
                .period(days + " days")
                .metrics(BacktestResult.BacktestMetrics.builder()
                        .totalReturn(totalReturn.setScale(4, RoundingMode.HALF_UP))
                        .sharpeRatio(BigDecimal.valueOf(sharpe).setScale(4, RoundingMode.HALF_UP))
                        .maxDrawdown(maxDrawdown.negate().setScale(4, RoundingMode.HALF_UP))
                        .winRate(winRate)
                        .sampleSize(returns.size())
                        .profitFactor(profitFactor)
                        .build())
                .computedAt(Instant.now())
                .build();

        return ResponseEntity.ok(result);
    }
}