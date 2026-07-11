package com.fintech.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelMetadata {
    private String symbol;
    private String modelVersion;
    private String modelType;
    private Instant trainedAt;
    private Instant lastRetrainingAt;
    private int featureCount;
    private int trainSamples;
    private int testSamples;
    private String status;
}