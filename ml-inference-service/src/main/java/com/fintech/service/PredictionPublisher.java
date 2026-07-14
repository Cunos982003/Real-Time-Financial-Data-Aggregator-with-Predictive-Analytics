package com.fintech.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionPublisher {

    private final ModelInference modelInference;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final List<String> STREAMED_SYMBOLS = List.of("BTCUSD", "ETHUSD", "XRPUSD");
    private static final String TOPIC = "prediction-stream";

    @Scheduled(fixedRate = 5000)
    public void publishPredictions() {
        for (String symbol : STREAMED_SYMBOLS) {
            try {
                Object prediction = modelInference.predict(symbol);
                kafkaTemplate.send(TOPIC, symbol, prediction)
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