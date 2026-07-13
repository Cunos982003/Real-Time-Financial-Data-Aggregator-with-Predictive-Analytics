package com.fintech.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/ws")
public class WebSocketController {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebSocketController(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("http://ml-inference:8083").build();
    }

    @GetMapping(value = "/stream/{symbol}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map>> stream(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "5000") long intervalMs) {

        log.info("SSE stream started for {} (interval={}ms)", symbol, intervalMs);

        // Emit current prediction immediately, then on interval
        return Flux.interval(Duration.ofMillis(0), Duration.ofMillis(intervalMs))
                .flatMap(tick -> fetchPrediction(symbol))
                .map(pred -> ServerSentEvent.<Map>builder()
                        .id(symbol)
                        .event("prediction")
                        .data(pred)
                        .build())
                .doOnCancel(() -> log.info("SSE stream ended for {}", symbol))
                .doOnError(e -> log.warn("SSE stream error for {}: {}", symbol, e.getMessage()));
    }

    private Mono<Map> fetchPrediction(String symbol) {
        return webClient.get()
                .uri("/api/v1/predictions/{symbol}", symbol)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .doOnNext(pred -> log.trace("SSE prediction tick for {}", symbol))
                .onErrorResume(e -> {
                    log.warn("Prediction fetch failed for {}: {}", symbol, e.getMessage());
                    return Mono.just(Map.<String, Object>of(
                            "symbol", symbol,
                            "error", "upstream unavailable",
                            "timestamp", Instant.now().toString()));
                });
    }
}