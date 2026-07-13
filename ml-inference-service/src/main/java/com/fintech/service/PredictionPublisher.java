package com.fintech.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionPublisher {

    private final ModelInference modelInference;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final List<String> STREAMED_SYMBOLS = List.of("BTCUSD", "ETHUSD", "XRPUSD");
    private static final String TOPIC = "prediction-stream";

    @Scheduled(fixedRate = 5000)
    public void publishPredictions() {
        for (String symbol : STREAMED_SYMBOLS) {
            try {
                Object prediction = modelInference.predict(symbol);
                String json = objectMapper.writeValueAsString(prediction);
                kafkaTemplate.send(TOPIC, symbol, json)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.warn("Failed to publish prediction for {}: {}", symbol, ex.getMessage());
                            }
                        });
            } catch (Exception e) {
                log.warn("Prediction publish error for {}: {}", symbol, e.getMessage());
            }
        }
    }
}