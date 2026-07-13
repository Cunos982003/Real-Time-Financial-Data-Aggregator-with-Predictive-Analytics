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
public class IexCloudConnector implements ExchangeConnector {

    private final ObjectMapper objectMapper;
    private final Map<String, Disposable> subscriptions = new ConcurrentHashMap<>();
    private final AtomicBoolean connected = new AtomicBoolean(false);

    @Value("${exchange.iexcloud.api-key:}")
    private String apiKey;

    @Value("${exchange.iexcloud.wss-url:wss://ws.iex.cloud/v1}")
    private String wssUrl;

    private static final Duration RETRY_MAX_BACKOFF = Duration.ofSeconds(30);

    @Override
    public String getExchangeName() {
        return "IEXCLOUD";
    }

    @Override
    public Flux<TickMessage> connect(Flux<String> symbols) {
        return symbols.flatMap(this::subscribeToSymbol, 1)
                .doOnSubscribe(s -> {
                    connected.set(true);
                    log.info("IEX Cloud connector connected");
                })
                .doOnTerminate(() -> {
                    connected.set(false);
                    log.info("IEX Cloud connector disconnected");
                })
                .doOnError(e -> {
                    connected.set(false);
                    log.error("IEX Cloud connector error: {}", e.getMessage());
                });
    }

    private Flux<TickMessage> subscribeToSymbol(String symbol) {
        String normalized = symbol.toUpperCase().replace("-", "");
        String urlWithToken = wssUrl + "?token=" + (apiKey.isEmpty() ? "demo" : apiKey);

        Disposable disposable = HttpClient.create()
                .websocket(WebsocketClientSpec.builder().build())
                .uri(URI.create(urlWithToken))
                .handle((inbound, outbound) -> {
                    String subscribeMsg = String.format(
                            "{\"type\":\"subscribe\",\"symbol\":\"%s\"}", normalized);
                    return outbound.sendString(Mono.just(subscribeMsg))
                            .then(inbound.receive().asString()
                                    .filter(line -> !line.isEmpty())
                                    .map(raw -> parseMessage(raw, normalized))
                                    .filter(msg -> msg != null)
                                    .doOnNext(msg -> log.trace("IEX Cloud tick: {} @ {}", msg.getSymbol(), msg.getPrice()))
                                    .doOnError(e -> log.error("IEX Cloud error for {}: {}", symbol, e.getMessage()))
                                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(5)).maxBackoff(RETRY_MAX_BACKOFF))
                                    .then(Mono.never()));
                })
                .subscribe(
                        msg -> {},
                        e -> log.error("IEX Cloud ws error for {}: {}", symbol, e.getMessage())
                );

        subscriptions.put(symbol, disposable);
        return Flux.empty();
    }

    private TickMessage parseMessage(String raw, String symbol) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            String type = node.path("type").asText("");

            if ("quote".equals(type)) {
                BigDecimal price = parseDecimal(node.path("last").asText());
                BigDecimal bid = parseDecimal(node.path("bid").asText());
                BigDecimal ask = parseDecimal(node.path("ask").asText());
                BigDecimal volume = parseDecimal(node.path("volume").asText());
                long timestamp = node.path("timestamp").asLong(System.currentTimeMillis());

                return TickMessage.builder()
                        .symbol(symbol)
                        .price(price)
                        .bidPrice(bid)
                        .askPrice(ask)
                        .volume(volume)
                        .exchange("IEXCLOUD")
                        .timestamp(Instant.ofEpochMilli(timestamp))
                        .ingestedAt(Instant.now())
                        .build();
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse IEX Cloud message: {}", e.getMessage());
            return null;
        }
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isEmpty() || "null".equals(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public void reconnect() {
        log.info("Reconnecting IEX Cloud connector");
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