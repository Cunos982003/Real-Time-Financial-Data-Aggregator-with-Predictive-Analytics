package com.fintech.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRegistry {

    private final RedisTemplate<String, Object> redisTemplate;
    private final Path modelBasePath = Paths.get(System.getenv().getOrDefault("MODEL_STORAGE_PATH", "/models"));

    private final Map<String, String> activeVersions = new ConcurrentHashMap<>();

    public void registerVersion(String symbol, String version) {
        activeVersions.put(symbol, version);
        try {
            redisTemplate.opsForValue().set("model:active:" + symbol, version, 24, TimeUnit.HOURS);
            log.info("Registered model version {} for {}", version, symbol);
        } catch (Exception e) {
            log.warn("Failed to cache active version: {}", e.getMessage());
        }
    }

    public Optional<String> getActiveVersion(String symbol) {
        try {
            Object cached = redisTemplate.opsForValue().get("model:active:" + symbol);
            if (cached != null) return Optional.of(cached.toString());
        } catch (Exception e) {
            log.debug("Redis lookup failed, using in-memory: {}", e.getMessage());
        }
        return Optional.ofNullable(activeVersions.get(symbol));
    }

    public Path getModelPath(String symbol, String version) {
        return modelBasePath.resolve(symbol).resolve(version);
    }

    public boolean modelExists(String symbol, String version) {
        return Files.exists(getModelPath(symbol, version));
    }
}