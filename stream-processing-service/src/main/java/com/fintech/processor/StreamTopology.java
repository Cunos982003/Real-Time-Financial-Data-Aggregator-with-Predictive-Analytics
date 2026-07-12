package com.fintech.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.model.FeatureVector;
import com.fintech.model.TickEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamTopology {

    private final StreamsBuilder streamsBuilder;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final TickAggregationProcessor tickAggregator;
    private final FeatureEngineeringProcessor featureEngine;

    @Value("${kafka.topics.raw-ticks:raw-ticks}")
    private String rawTicksTopic;

    @Value("${kafka.topics.features:features}")
    private String featuresTopic;

    @Value("${kafka.topics.candles:candles}")
    private String candlesTopic;

    @Autowired
    public StreamTopology(KafkaTemplate<String, Object> kafkaTemplate, ObjectMapper objectMapper,
                          TickAggregationProcessor tickAggregator, FeatureEngineeringProcessor featureEngine) {
        this.streamsBuilder = new StreamsBuilder();
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.tickAggregator = tickAggregator;
        this.featureEngine = featureEngine;
    }

    @PostConstruct
    public void buildTopology() {
        KStream<String, String> tickStream = streamsBuilder.stream(rawTicksTopic,
                Consumed.with(Serdes.String(), Serdes.String()));

        tickStream.foreach((key, value) -> {
            try {
                TickEvent tick = objectMapper.readValue(value, TickEvent.class);
                processTickEvent(tick);
            } catch (Exception e) {
                log.error("Failed to process tick: {}", e.getMessage());
            }
        });
    }

    private void processTickEvent(TickEvent tick) {
        String symbol = tick.getSymbol();
        Instant now = tick.getTimestamp() != null ? tick.getTimestamp() : Instant.now();

        tickAggregator.processTick(symbol, tick.getPrice(), tick.getVolume(),
                tick.getBidPrice(), tick.getAskPrice(), now);

        var candles = tickAggregator.flushExpiredCandles(symbol, now);
        for (var candle : candles) {
            try {
                kafkaTemplate.send(candlesTopic, symbol, candle);
            } catch (Exception e) {
                log.error("Failed to publish candle: {}", e.getMessage());
            }
        }

        if (!candles.isEmpty()) {
            var latestCandle = candles.get(candles.size() - 1);
            FeatureVector features = featureEngine.computeFeatures(
                    symbol, latestCandle, tick.getPrice(), tick.getBidPrice(), tick.getAskPrice());
            try {
                kafkaTemplate.send(featuresTopic, symbol, features);
                log.debug("Published features for {}: RSI={}, MACD={}", symbol,
                        features.getRsi14(), features.getMacd());
            } catch (Exception e) {
                log.error("Failed to publish features: {}", e.getMessage());
            }
        }
    }
}