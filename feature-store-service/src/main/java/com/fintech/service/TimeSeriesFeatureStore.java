package com.fintech.service;

import com.fintech.entity.FeatureEntity;
import com.fintech.entity.PredictionEntity;
import com.fintech.repository.FeatureRepository;
import com.fintech.repository.PredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeSeriesFeatureStore {

    private final FeatureRepository featureRepository;
    private final PredictionRepository predictionRepository;

    @Transactional
    public FeatureEntity saveFeature(FeatureEntity feature) {
        return featureRepository.save(feature);
    }

    public Optional<FeatureEntity> getLatestFeature(String symbol) {
        return featureRepository.findTopBySymbolOrderByTimestampDesc(symbol);
    }

    public List<FeatureEntity> getFeatures(String symbol, Instant since) {
        return featureRepository.findBySymbolSince(symbol, since);
    }

    public List<FeatureEntity> getFeaturesWindow(String symbol, int lookbackSeconds) {
        Instant since = Instant.now().minus(lookbackSeconds, ChronoUnit.SECONDS);
        return featureRepository.findBySymbolSince(symbol, since);
    }

    @Transactional
    public PredictionEntity savePrediction(PredictionEntity prediction) {
        return predictionRepository.save(prediction);
    }

    public Optional<PredictionEntity> getLatestPrediction(String symbol) {
        return predictionRepository.findTopBySymbolOrderByTimestampDesc(symbol);
    }

    public List<PredictionEntity> getPredictionHistory(String symbol, int lookbackDays) {
        Instant since = Instant.now().minus(lookbackDays, ChronoUnit.DAYS);
        return predictionRepository.findBySymbolSince(symbol, since);
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupOldFeatures() {
        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        featureRepository.deleteByTimestampBefore(cutoff);
        predictionRepository.deleteByTimestampBefore(cutoff);
        log.info("Cleaned up features and predictions older than {}", cutoff);
    }
}