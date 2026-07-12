package com.fintech.alerting;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AlertService {

    private final WebClient.Builder webClientBuilder;
    private final Map<String, Long> lastAlertTime = new ConcurrentHashMap<>();
    private static final long ALERT_COOLDOWN_MS = 300000;

    public AlertService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public void checkAndAlert(String symbol, String level, String message, Map<String, Object> data) {
        String alertId = symbol + "_" + level;
        long now = System.currentTimeMillis();
        Long lastAlert = lastAlertTime.get(alertId);

        if (lastAlert != null && (now - lastAlert) < ALERT_COOLDOWN_MS) {
            return;
        }

        lastAlertTime.put(alertId, now);

        Map<String, Object> alert = Map.of(
                "type", "alert",
                "level", level,
                "symbol", symbol,
                "message", message,
                "data", data,
                "timestamp", Instant.now().toString()
        );

        log.warn("ALERT [{}] {}: {} - {}", level, symbol, message, data);

        try {
            WebClient webClient = webClientBuilder.baseUrl("http://gateway:8080").build();
            webClient.post().uri("/ws/alerts").bodyValue(alert).retrieve().toEntity(Void.class).block();
        } catch (Exception e) {
            log.debug("Alert broadcast failed (non-critical): {}", e.getMessage());
        }
    }

    public void alertHighLatency(String symbol, double latencyMs) {
        if (latencyMs > 500) {
            checkAndAlert(symbol, "WARNING",
                    String.format("High prediction latency detected: %.2fms", latencyMs),
                    Map.of("latencyMs", latencyMs, "threshold", 500));
        }
    }

    public void alertModelDrift(String symbol, double driftScore) {
        if (driftScore > 0.05) {
            checkAndAlert(symbol, "CRITICAL",
                    String.format("Model drift detected for %s: %.4f", symbol, driftScore),
                    Map.of("driftScore", driftScore, "threshold", "0.05"));
        }
    }

    public void alertKafkaLag(String topic, long lag) {
        if (lag > 1000) {
            log.warn("ALERT [WARNING] Kafka lag on {}: {}", topic, lag);
        }
    }

    public void alertServiceDown(String serviceName) {
        checkAndAlert(serviceName, "CRITICAL",
                "Service " + serviceName + " is down",
                Map.of("service", serviceName));
    }
}