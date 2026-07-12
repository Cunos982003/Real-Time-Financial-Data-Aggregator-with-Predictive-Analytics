package com.fintech.controller;

import com.fintech.config.ExchangeConfig;
import com.fintech.connector.ExchangeConnectorFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class HealthController {

    private final ExchangeConnectorFactory connectorFactory;
    private final ExchangeConfig exchangeConfig;

    @GetMapping("/actuator/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "data-ingestion-service",
                "exchanges", connectorFactory.getSupportedExchanges(),
                "symbols", exchangeConfig.getSymbols()
        ));
    }
}