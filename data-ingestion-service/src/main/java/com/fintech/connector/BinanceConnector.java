package com.fintech.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.WebsocketClientSpec;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class BinanceConnector implements ExchangeConnector {

    private final ObjectMapper objectMapper;
    private final Map<String, Disposable> subscriptions = new ConcurrentHashMap<>();
    private final AtomicBoolean connected = new AtomicBoolean(false);

    @Value("${exchange.binance.api-key:}")
    private String apiKey;

    private static final String WS_BASE = "wss://stream.binance.com:9443/ws";

    @PostConstruct
    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public String getExchangeName() {
        return "BINANCE";
    }

    @Override
    public Flux<TickMessage> connect(Flux<String> symbols) {
        return symbols.flatMap(this::subscribeToSymbol, 1)
                .doOnSubscribe(s -> {
                    connected.set(true);
                    log.info("Binance connector connected");
                })
                .doOnTerminate(() -> {
                    connected.set(false);
                    log.info("Binance connector disconnected");
                })
                .doOnError(e -> {
                    connected.set(false);
                    log.error("Binance connector error: {}", e.getMessage());
                });
    }

    private Flux<TickMessage> subscribeToSymbol(String symbol) {
        String normalized = symbol.toUpperCase().replace("-", "");
        String streamSymbol = normalized.toLowerCase();
        String wsUrl = WS_BASE + "/" + streamSymbol + "@ticker";

        Disposable disposable = HttpClient.create()
                .headers(h -> h.set("Upgrade", "websocket"))
                .websocket(WebsocketClientSpec.builder()
                        .protocols("stream.binance.com")
                        .build())
                .uri(URI.create(wsUrl))
                .handle((inbound, outbound) -> {
                    inbound.receive().asString()
                            .filter(line -> !line.isEmpty())
                            .map(raw -> parseTickerMessage(raw, normalized))
                            .filter(msg -> msg != null)
                            .doOnNext(msg -> log.trace("Binance tick: {} @ {}", msg.getSymbol(), msg.getPrice()))
                            .doOnError(e -> log.error("Binance error for {}: {}", symbol, e.getMessage()))
                            .retryWhen(Retry.backoff(3, Duration.ofSeconds(5)).maxBackoff(Duration.ofSeconds(30)))
                            .subscribe();
                    return outbound.neverComplete();
                })
                .subscribe(
                        msg -> {},
                        e -> log.error("Binance ws error for {}: {}", symbol, e.getMessage())
                );

        subscriptions.put(symbol, disposable);
        return Flux.empty();
    }

    private TickMessage parseTickerMessage(String raw, String symbol) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (!"24hrTicker".equals(node.path("e").asText())) {
                return null;
            }
            return TickMessage.builder()
                    .symbol(symbol)
                    .price(new BigDecimal(node.path("c").asText()))
                    .bidPrice(new BigDecimal(node.path("b").asText()))
                    .askPrice(new BigDecimal(node.path("a").asText()))
                    .volume(new BigDecimal(node.path("v").asText()))
                    .bidVolume(new BigDecimal(node.path("B").asText()))
                    .askVolume(new BigDecimal(node.path("A").asText()))
                    .exchange("BINANCE")
                    .timestamp(Instant.ofEpochMilli(node.path("E").asLong()))
                    .ingestedAt(Instant.now())
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse Binance ticker: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void reconnect() {
        log.info("Reconnecting Binance connector");
        disconnect();
        connected.set(true);
    }

    @Override
    public void disconnect() {
        subscriptions.values().forEach(Disposable::dispose);
        subscriptions.clear();
        connected.set(false);
    }

    @PreDestroy
    public void cleanup() {
        disconnect();
    }
}