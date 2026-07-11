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
public class ModelMetrics {
    private String symbol;
    private String modelVersion;
    private TrainingMetrics trainingMetrics;
    private String driftStatus;
    private BigDecimal driftScore;
    private Instant lastRetrainingDate;
    private Instant nextRetrainingDate;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrainingMetrics {
        private BigDecimal accuracy;
        private BigDecimal auc;
        private BigDecimal precision;
        private BigDecimal recall;
    }
}