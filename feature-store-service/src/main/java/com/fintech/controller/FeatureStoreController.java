package com.fintech.controller;

import com.fintech.model.FeatureVector;
import com.fintech.service.FeatureStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FeatureStoreController {

    private final FeatureStoreService featureStoreService;

    @GetMapping("/features/{symbol}")
    public ResponseEntity<List<FeatureVector>> getFeatures(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "3600") int lookbackSeconds) {
        List<FeatureVector> features = featureStoreService.getFeatureWindow(symbol, lookbackSeconds);
        if (features.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(features);
    }

    @GetMapping("/features/{symbol}/latest")
    public ResponseEntity<FeatureVector> getLatestFeature(@PathVariable String symbol) {
        return featureStoreService.getLatestFeature(symbol)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/features")
    public ResponseEntity<Void> storeFeature(@RequestBody FeatureVector feature) {
        try {
            featureStoreService.storeFeature(feature);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to store feature: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}