package com.fintech.connector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TickMessage implements Serializable {
    private String symbol;
    private BigDecimal price;
    private BigDecimal bidPrice;
    private BigDecimal askPrice;
    private BigDecimal volume;
    private BigDecimal bidVolume;
    private BigDecimal askVolume;
    private String exchange;
    private Instant timestamp;
    private Instant ingestedAt;

    public static TickMessage of(String symbol, BigDecimal price, String exchange) {
        return TickMessage.builder()
                .symbol(symbol)
                .price(price)
                .exchange(exchange)
                .timestamp(Instant.now())
                .ingestedAt(Instant.now())
                .build();
    }
}