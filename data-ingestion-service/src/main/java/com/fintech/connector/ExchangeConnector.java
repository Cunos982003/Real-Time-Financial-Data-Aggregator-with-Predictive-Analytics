package com.fintech.connector;

import reactor.core.publisher.Flux;

public interface ExchangeConnector {
    String getExchangeName();
    Flux<TickMessage> connect(Flux<String> symbols);
    boolean isConnected();
    void reconnect();
    void disconnect();
}