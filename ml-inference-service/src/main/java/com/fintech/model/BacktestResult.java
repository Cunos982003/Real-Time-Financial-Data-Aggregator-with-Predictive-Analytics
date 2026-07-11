package com.fintech.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResult {
    private String symbol;
    private String period;
    private BacktestMetrics metrics;
    private Instant computedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BacktestMetrics {
        private BigDecimal totalReturn;
        private BigDecimal sharpeRatio;
        private BigDecimal maxDrawdown;
        private BigDecimal winRate;
        private Integer sampleSize;
        private BigDecimal profitFactor;
    }
}