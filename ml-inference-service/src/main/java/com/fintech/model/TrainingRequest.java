package com.fintech.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingRequest {
    @Builder.Default
    @JsonProperty("lookback_days")
    private int lookbackDays = 30;
    @Builder.Default
    @JsonProperty("test_split")
    private double testSplit = 0.2;
    @Builder.Default
    @JsonProperty("model_type")
    private String modelType = "mlp";
    private String symbol;
}