package com.fintech.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureVector {
    private String symbol;
    private Instant timestamp;
    private Instant openTime;
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
    private BigDecimal volumeImbalance;
    private BigDecimal price;
    private BigDecimal volume;
    private int hourOfDay;
    private int dayOfWeek;
}