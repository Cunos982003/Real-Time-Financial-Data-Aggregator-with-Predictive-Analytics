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
    private Integer hourOfDay;
    private Integer dayOfWeek;
    private BigDecimal label;

    public double[] toFeatureArray() {
        return new double[]{
                rsi14 != null ? rsi14.doubleValue() : 50.0,
                macd != null ? macd.doubleValue() : 0.0,
                macdSignal != null ? macdSignal.doubleValue() : 0.0,
                macdHistogram != null ? macdHistogram.doubleValue() : 0.0,
                volatilityStd != null ? volatilityStd.doubleValue() : 0.0,
                bbWidth != null ? bbWidth.doubleValue() : 0.0,
                volumeMaRatio != null ? volumeMaRatio.doubleValue() : 1.0,
                priceMomentum != null ? priceMomentum.doubleValue() : 0.0,
                rateOfChange != null ? rateOfChange.doubleValue() : 0.0,
                atr14 != null ? atr14.doubleValue() : 0.0,
                bidAskSpread != null ? bidAskSpread.doubleValue() : 0.001,
                price != null ? price.doubleValue() : 0.0,
                volume != null ? volume.doubleValue() : 0.0,
                hourOfDay != null ? hourOfDay : 12,
                dayOfWeek != null ? dayOfWeek : 3
        };
    }

    public int label() {
        return label != null ? label.intValue() : 0;
    }
}