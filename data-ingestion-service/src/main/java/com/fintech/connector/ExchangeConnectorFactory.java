package com.fintech.connector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeConnectorFactory {

    private final Map<String, ExchangeConnector> connectors;

    public ExchangeConnector getConnector(String exchangeName) {
        ExchangeConnector connector = connectors.get(exchangeName.toUpperCase());
        if (connector == null) {
            throw new IllegalArgumentException("Unsupported exchange: " + exchangeName);
        }
        return connector;
    }

    public List<String> getSupportedExchanges() {
        return connectors.keySet().stream().sorted().toList();
    }

    public void reconnectAll() {
        connectors.values().forEach(ExchangeConnector::reconnect);
    }

    public void disconnectAll() {
        connectors.values().forEach(ExchangeConnector::disconnect);
    }
}