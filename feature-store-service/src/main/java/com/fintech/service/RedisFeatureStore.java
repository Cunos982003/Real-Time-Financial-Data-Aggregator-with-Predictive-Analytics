package com.fintech.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RedisFeatureStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisFeatureStore(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void cacheFeature(String symbol, String featureJson) {
        String key = "features:" + symbol;
        try {
            redisTemplate.opsForValue().set(key, featureJson, 24, TimeUnit.HOURS);
            redisTemplate.opsForList().rightPush("features:recent:" + symbol, featureJson);
            redisTemplate.opsForList().trim("features:recent:" + symbol, 0, 1000);
        } catch (Exception e) {
            log.warn("Failed to cache feature for {}: {}", symbol, e.getMessage());
        }
    }

    public Optional<String> getCachedFeature(String symbol) {
        String key = "features:" + symbol;
        try {
            Object val = redisTemplate.opsForValue().get(key);
            if (val != null) {
                return Optional.of(val.toString());
            }
        } catch (Exception e) {
            log.warn("Failed to get cached feature for {}: {}", symbol, e.getMessage());
        }
        return Optional.empty();
    }

    public void cachePrediction(String symbol, String predictionJson) {
        String key = "predictions:" + symbol;
        try {
            redisTemplate.opsForValue().set(key, predictionJson, 60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to cache prediction for {}: {}", symbol, e.getMessage());
        }
    }

    public Optional<String> getCachedPrediction(String symbol) {
        String key = "predictions:" + symbol;
        try {
            Object val = redisTemplate.opsForValue().get(key);
            if (val != null) {
                return Optional.of(val.toString());
            }
        } catch (Exception e) {
            log.warn("Failed to get cached prediction for {}: {}", symbol, e.getMessage());
        }
        return Optional.empty();
    }

    public void invalidate(String pattern) {
        try {
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate pattern {}: {}", pattern, e.getMessage());
        }
    }
}