package com.fintech.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
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
public class KrakenConnector implements ExchangeConnector {

    private final ObjectMapper objectMapper;
    private final Map<String, Disposable> subscriptions = new ConcurrentHashMap<>();
    private final AtomicBoolean connected = new AtomicBoolean(false);

    @Value("${exchange.kraken.api-key:}")
    private String apiKey;

    private static final String WS_URL = "wss://ws.kraken.com";

    @Override
    public String getExchangeName() {
        return "KRAKEN";
    }

    @Override
    public Flux<TickMessage> connect(Flux<String> symbols) {
        return symbols.flatMap(this::subscribeToSymbol, 1)
                .doOnSubscribe(s -> {
                    connected.set(true);
                    log.info("Kraken connector connected");
                })
                .doOnError(e -> {
                    connected.set(false);
                    log.error("Kraken connector error: {}", e.getMessage());
                });
    }

    private Flux<TickMessage> subscribeToSymbol(String symbol) {
        Disposable disposable = HttpClient.create()
                .headers(h -> h.set("Upgrade", "websocket"))
                .websocket(WebsocketClientSpec.builder().protocols("null").build())
                .uri(URI.create(WS_URL))
                .handle((inbound, outbound) -> {
                    String subscribeMsg = String.format(
                            "{\"event\":\"subscribe\",\"pair\":[\"%s\"],\"subscription\":{\"name\":\"ticker\"}}",
                            symbol.replace("-", "/"));
                    return outbound.sendString(Mono.just(subscribeMsg))
                            .then(inbound.receive().asString()
                                    .filter(line -> !line.isEmpty())
                                    .map(raw -> parseTickerMessage(raw, symbol))
                                    .filter(msg -> msg != null)
                                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(5)).maxBackoff(Duration.ofSeconds(30)))
                                    .doOnError(e -> log.error("Kraken WebSocket error for {}: {}", symbol, e.getMessage()))
                                    .then(Mono.never()));
                })
                .subscribe();
        subscriptions.put(symbol, disposable);
        return Flux.empty();
    }

    private TickMessage parseTickerMessage(String raw, String symbol) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.isArray() && node.size() >= 4) {
                JsonNode tickerData = node.get(1);
                if (tickerData == null || !tickerData.isObject()) return null;
                BigDecimal price = new BigDecimal(tickerData.path("c").get(0).asText());
                BigDecimal bid = new BigDecimal(tickerData.path("b").get(0).asText());
                BigDecimal ask = new BigDecimal(tickerData.path("a").get(0).asText());
                BigDecimal volume = new BigDecimal(tickerData.path("v").get(1).asText());
                return TickMessage.builder()
                        .symbol(symbol)
                        .price(price)
                        .bidPrice(bid)
                        .askPrice(ask)
                        .volume(volume)
                        .exchange("KRAKEN")
                        .timestamp(Instant.now())
                        .ingestedAt(Instant.now())
                        .build();
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse Kraken ticker: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public void reconnect() {
        log.info("Reconnecting Kraken connector");
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