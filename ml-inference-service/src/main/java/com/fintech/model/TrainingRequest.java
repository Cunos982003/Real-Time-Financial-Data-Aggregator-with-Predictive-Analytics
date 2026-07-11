package com.fintech.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingRequest {
    @Builder.Default private int lookbackDays = 30;
    @Builder.Default private double testSplit = 0.2;
    @Builder.Default private String modelType = "mlp";
    private String symbol;
}