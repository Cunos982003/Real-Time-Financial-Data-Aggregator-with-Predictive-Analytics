package com.fintech.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "exchange")
public class ExchangeConfig {
    private List<String> symbols = List.of("BTCUSD", "ETHUSD", "XRPUSD");
    private List<String> enabledExchanges = List.of("BINANCE", "COINBASE", "KRAKEN", "YAHOO");
    private String defaultExchange = "BINANCE";
    private int reconnectDelaySeconds = 5;
    private int maxReconnectAttempts = 10;
    private boolean simulationMode = false;
}