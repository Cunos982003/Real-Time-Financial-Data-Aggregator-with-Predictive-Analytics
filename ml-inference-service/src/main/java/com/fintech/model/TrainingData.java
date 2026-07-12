package com.fintech.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingData {
    private String symbol;
    private List<double[]> features;
    private List<Integer> labels;
    private int lookbackDays;
    private Instant startDate;
    private Instant endDate;

    public int size() {
        return labels != null ? labels.size() : 0;
    }
}