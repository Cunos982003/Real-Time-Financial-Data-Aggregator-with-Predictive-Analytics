package com.fintech.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PredictionController {

    private final WebClient webClient = WebClient.builder().build();

    private <T> T get(String baseUrl, String path, Function<String, String> uriFunc,
                     Class<T> responseType, Duration timeout, T fallback) {
        try {
            return webClient.get()
                    .uri(uriFunc.apply(path))
                    .retrieve()
                    .toEntity(responseType)
                    .block(timeout)
                    .getBody();
        } catch (Exception e) {
            log.error("Request failed ({}): {}", path, e.getMessage());
            return fallback;
        }
    }

    @GetMapping("/predictions/{symbol}")
    public Map<String, Object> getPrediction(@PathVariable String symbol) {
        return get("http://ml-inference:8083",
                "/api/v1/predictions/" + symbol,
                p -> p,
                Map.class,
                Duration.ofSeconds(5),
                Map.of("symbol", symbol,
                        "prediction", 0,
                        "confidence", BigDecimal.valueOf(0.5),
                        "probabilityUp", BigDecimal.valueOf(0.5),
                        "probabilityDown", BigDecimal.valueOf(0.5),
                        "modelVersion", "unknown",
                        "latestPrice", BigDecimal.valueOf(0),
                        "timestamp", Instant.now().toString()));
    }

    @GetMapping("/features/{symbol}")
    public Map<String, Object> getFeatures(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "3600") int lookbackSeconds) {
        return get("http://feature-store:8084",
                "/features/" + symbol + "?lookbackSeconds=" + lookbackSeconds,
                p -> p,
                Map.class,
                Duration.ofSeconds(5),
                Map.of("symbol", symbol, "lookbackSeconds", lookbackSeconds,
                        "data", List.of()));
    }

    @GetMapping("/backtest/{symbol}")
    public Map<String, Object> backtest(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "30") int days) {
        return get("http://ml-inference:8083",
                "/api/v1/backtest/" + symbol + "?days=" + days,
                p -> p,
                Map.class,
                Duration.ofSeconds(10),
                Map.of("symbol", symbol, "period", days + " days",
                        "error", "service unavailable"));
    }

    @GetMapping("/models/{symbol}/metrics")
    public Map<String, Object> getModelMetrics(@PathVariable String symbol) {
        return get("http://ml-inference:8083",
                "/api/v1/models/" + symbol + "/metrics",
                p -> p,
                Map.class,
                Duration.ofSeconds(5),
                Map.of("symbol", symbol, "error", "service unavailable"));
    }
}