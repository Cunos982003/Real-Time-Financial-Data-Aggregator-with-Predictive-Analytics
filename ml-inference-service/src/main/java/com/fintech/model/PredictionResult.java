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
public class PredictionResult {
    private String symbol;
    private Integer prediction;
    private BigDecimal confidence;
    private BigDecimal probabilityUp;
    private BigDecimal probabilityDown;
    private String modelVersion;
    private BigDecimal latestPrice;
    private Instant timestamp;
    private Long inferenceMs;
}