package com.fintech.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.entity.FeatureEntity;
import com.fintech.entity.PredictionEntity;
import com.fintech.model.FeatureVector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureStoreService {

    private final RedisFeatureStore redisStore;
    private final TimeSeriesFeatureStore tsStore;
    private final ObjectMapper objectMapper;

    @Transactional
    public void storeFeature(FeatureVector feature) {
        FeatureEntity entity = mapToEntity(feature);
        tsStore.saveFeature(entity);

        try {
            String json = objectMapper.writeValueAsString(feature);
            redisStore.cacheFeature(feature.getSymbol(), json);
        } catch (Exception e) {
            log.warn("Failed to cache feature: {}", e.getMessage());
        }
    }

    public Optional<FeatureVector> getLatestFeature(String symbol) {
        Optional<String> cached = redisStore.getCachedFeature(symbol);
        if (cached.isPresent()) {
            try {
                return Optional.of(objectMapper.readValue(cached.get(), FeatureVector.class));
            } catch (Exception e) {
                log.warn("Failed to parse cached feature: {}", e.getMessage());
            }
        }

        Optional<FeatureEntity> entity = tsStore.getLatestFeature(symbol);
        return entity.map(this::mapToVector);
    }

    public List<FeatureVector> getFeatureWindow(String symbol, int lookbackSeconds) {
        return tsStore.getFeaturesWindow(symbol, lookbackSeconds).stream()
                .map(this::mapToVector)
                .collect(Collectors.toList());
    }

    @Transactional
    public void storePrediction(String symbol, int prediction, BigDecimal confidence,
                                 BigDecimal probUp, BigDecimal probDown,
                                 String modelVersion, BigDecimal price) {
        PredictionEntity entity = PredictionEntity.builder()
                .symbol(symbol)
                .timestamp(Instant.now())
                .prediction(prediction)
                .confidence(confidence)
                .probabilityUp(probUp)
                .probabilityDown(probDown)
                .modelVersion(modelVersion)
                .latestPrice(price)
                .featureTimestamp(Instant.now())
                .createdAt(Instant.now())
                .build();
        entity = tsStore.savePrediction(entity);
        try {
            redisStore.cachePrediction(symbol, objectMapper.writeValueAsString(entity));
        } catch (Exception e) {
            log.warn("Failed to cache prediction: {}", e.getMessage());
        }
    }

    public Optional<PredictionEntity> getLatestPrediction(String symbol) {
        return tsStore.getLatestPrediction(symbol);
    }

    public List<PredictionEntity> getPredictionHistory(String symbol, int lookbackDays) {
        return tsStore.getPredictionHistory(symbol, lookbackDays);
    }

    private FeatureEntity mapToEntity(FeatureVector fv) {
        return FeatureEntity.builder()
                .symbol(fv.getSymbol())
                .timestamp(fv.getTimestamp())
                .rsi14(fv.getRsi14())
                .macd(fv.getMacd())
                .macdSignal(fv.getMacdSignal())
                .macdHistogram(fv.getMacdHistogram())
                .volatilityStd(fv.getVolatilityStd())
                .bbWidth(fv.getBbWidth())
                .bbUpper(fv.getBbUpper())
                .bbLower(fv.getBbLower())
                .volumeMaRatio(fv.getVolumeMaRatio())
                .priceMomentum(fv.getPriceMomentum())
                .rateOfChange(fv.getRateOfChange())
                .atr14(fv.getAtr14())
                .bidAskSpread(fv.getBidAskSpread())
                .price(fv.getPrice())
                .volume(fv.getVolume())
                .hourOfDay(fv.getHourOfDay())
                .dayOfWeek(fv.getDayOfWeek())
                .createdAt(Instant.now())
                .build();
    }

    private FeatureVector mapToVector(FeatureEntity e) {
        return FeatureVector.builder()
                .symbol(e.getSymbol())
                .timestamp(e.getTimestamp())
                .rsi14(e.getRsi14())
                .macd(e.getMacd())
                .macdSignal(e.getMacdSignal())
                .macdHistogram(e.getMacdHistogram())
                .volatilityStd(e.getVolatilityStd())
                .bbWidth(e.getBbWidth())
                .bbUpper(e.getBbUpper())
                .bbLower(e.getBbLower())
                .volumeMaRatio(e.getVolumeMaRatio())
                .priceMomentum(e.getPriceMomentum())
                .rateOfChange(e.getRateOfChange())
                .atr14(e.getAtr14())
                .bidAskSpread(e.getBidAskSpread())
                .price(e.getPrice())
                .volume(e.getVolume())
                .hourOfDay(e.getHourOfDay())
                .dayOfWeek(e.getDayOfWeek())
                .build();
    }
}