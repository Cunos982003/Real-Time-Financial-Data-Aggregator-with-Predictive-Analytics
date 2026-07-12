package com.fintech.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriftResult {
    private String symbol;
    private double klDivergence;
    private double psi;
    private boolean driftDetected;
    private double threshold;
    private String status;
    private java.time.Instant detectedAt;
}