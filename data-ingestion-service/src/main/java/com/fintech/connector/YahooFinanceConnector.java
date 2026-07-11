package com.fintech.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class YahooFinanceConnector implements ExchangeConnector {

    private final ObjectMapper objectMapper;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    @Value("${exchange.yahoo.api-key:}")
    private String apiKey;

    @Override
    public String getExchangeName() {
        return "YAHOO";
    }

    @Override
    public Flux<TickMessage> connect(Flux<String> symbols) {
        return symbols.flatMap(this::fetchQuote)
                .doOnSubscribe(s -> {
                    connected.set(true);
                    log.info("Yahoo Finance connector connected");
                })
                .doOnError(e -> {
                    connected.set(false);
                    log.error("Yahoo Finance connector error: {}", e.getMessage());
                });
    }

    private Flux<TickMessage> fetchQuote(String symbol) {
        String url = String.format(
                "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1m&range=1d",
                symbol);
        return HttpClient.create()
                .get()
                .uri(url)
                .responseSingle((resp, body) -> body.asString())
                .map(raw -> parseQuote(raw, symbol))
                .filter(msg -> msg != null)
                .repeatWhen(counter -> counter.delayUntil(i -> Flux.interval(Duration.ofSeconds(30))))
                .doOnError(e -> log.error("Yahoo Finance polling error for {}: {}", symbol, e.getMessage()));
    }

    private TickMessage parseQuote(String raw, String symbol) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode result = root.path("chart").path("result");
            if (result.isEmpty()) return null;
            JsonNode meta = result.get(0).path("meta");
            BigDecimal price = new BigDecimal(meta.path("regularMarketPrice").asText());
            BigDecimal bid = new BigDecimal(meta.path("bid").asText("0"));
            BigDecimal ask = new BigDecimal(meta.path("ask").asText("0"));
            long timestamp = meta.path("regularMarketTime").asLong(0);
            return TickMessage.builder()
                    .symbol(symbol)
                    .price(price)
                    .bidPrice(bid)
                    .askPrice(ask)
                    .volume(new BigDecimal(meta.path("regularMarketVolume").asText("0")))
                    .exchange("YAHOO")
                    .timestamp(timestamp > 0 ? Instant.ofEpochSecond(timestamp) : Instant.now())
                    .ingestedAt(Instant.now())
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse Yahoo Finance quote: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public void reconnect() {
        log.info("Reconnecting Yahoo Finance connector");
        connected.set(true);
    }

    @Override
    public void disconnect() {
        connected.set(false);
    }

    @PreDestroy
    public void cleanup() {
        disconnect();
    }
}