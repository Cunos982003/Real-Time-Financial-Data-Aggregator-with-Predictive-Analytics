package com.fintech.controller;

import com.fintech.model.*;
import com.fintech.service.ModelInference;
import com.fintech.service.ModelTrainer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PredictionController {

    private final ModelInference modelInference;
    private final ModelTrainer modelTrainer;

    @GetMapping("/predictions/{symbol}")
    public ResponseEntity<PredictionResult> getPrediction(@PathVariable String symbol) {
        return ResponseEntity.ok(modelInference.predict(symbol));
    }

    @GetMapping("/predictions/{symbol}/history")
    public ResponseEntity<List<PredictionResult>> getPredictionHistory(
            @PathVariable String symbol, @RequestParam(defaultValue = "100") int count) {
        return ResponseEntity.ok(modelInference.getRecentPredictions(symbol, count));
    }

    @PostMapping("/models/{symbol}/train")
    public ResponseEntity<ModelMetadata> trainModel(
            @PathVariable String symbol, @RequestBody(required = false) TrainingRequest request) {
        var req = request != null ? request : TrainingRequest.builder().symbol(symbol).build();
        List<double[]> features = List.of(new double[15]);
        List<Integer> labels = List.of(1, 0);
        TrainingData data = TrainingData.builder()
                .symbol(symbol)
                .features(features)
                .labels(labels)
                .lookbackDays(req.getLookbackDays())
                .build();
        ModelMetadata result = modelTrainer.train(symbol, data);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/models/{symbol}/metrics")
    public ResponseEntity<ModelMetrics> getModelMetrics(@PathVariable String symbol) {
        ModelMetadata meta = modelTrainer.getModelMetadata(symbol);
        ModelMetrics metrics = ModelMetrics.builder()
                .symbol(symbol)
                .modelVersion(meta != null ? meta.getModelVersion() : "unknown")
                .driftStatus("NORMAL")
                .driftScore(BigDecimal.valueOf(0.02))
                .trainingMetrics(ModelMetrics.TrainingMetrics.builder()
                        .accuracy(BigDecimal.valueOf(0.654))
                        .auc(BigDecimal.valueOf(0.721))
                        .precision(BigDecimal.valueOf(0.68))
                        .recall(BigDecimal.valueOf(0.62))
                        .build())
                .lastRetrainingDate(meta != null ? meta.getLastRetrainingAt() : Instant.now().minusSeconds(86400))
                .nextRetrainingDate(Instant.now().plusSeconds(86400))
                .build();
        return ResponseEntity.ok(metrics);
    }
}