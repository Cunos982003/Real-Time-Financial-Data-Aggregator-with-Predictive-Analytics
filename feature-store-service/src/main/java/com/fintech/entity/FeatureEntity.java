package com.fintech.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "features", indexes = {
        @Index(name = "idx_features_symbol_timestamp", columnList = "symbol, timestamp DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false)
    private Instant timestamp;

    private BigDecimal rsi14;
    private BigDecimal macd;
    private BigDecimal macdSignal;
    private BigDecimal macdHistogram;
    private BigDecimal volatilityStd;
    private BigDecimal bbWidth;
    private BigDecimal bbUpper;
    private BigDecimal bbLower;
    private BigDecimal volumeMaRatio;
    private BigDecimal priceMomentum;
    private BigDecimal rateOfChange;
    private BigDecimal atr14;
    private BigDecimal bidAskSpread;
    private BigDecimal price;
    private BigDecimal volume;
    private Integer hourOfDay;
    private Integer dayOfWeek;
    private String exchange;
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}