package com.fintech.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureVectorDTO {
    private String symbol;
    private Instant timestamp;
    private Double rsi14;
    private Double macd;
    private Double macdSignal;
    private Double macdHistogram;
    private Double volatilityStd;
    private Double bbWidth;
    private Double bbUpper;
    private Double bbLower;
    private Double volumeMaRatio;
    private Double priceMomentum;
    private Double rateOfChange;
    private Double atr14;
    private Double bidAskSpread;
    private Double volumeImbalance;
    private Double price;
    private Double volume;
    private Integer hourOfDay;
    private Integer dayOfWeek;
    private Integer label;
    private String exchange;

    public double[] toFeatureArray() {
        return new double[]{
                rsi14 != null ? rsi14 : 50.0,
                macd != null ? macd : 0.0,
                macdSignal != null ? macdSignal : 0.0,
                macdHistogram != null ? macdHistogram : 0.0,
                volatilityStd != null ? volatilityStd : 0.0,
                bbWidth != null ? bbWidth : 0.0,
                volumeMaRatio != null ? volumeMaRatio : 1.0,
                priceMomentum != null ? priceMomentum : 0.0,
                rateOfChange != null ? rateOfChange : 0.0,
                atr14 != null ? atr14 : 0.0,
                bidAskSpread != null ? bidAskSpread : 0.001,
                price != null ? price : 0.0,
                volume != null ? volume : 0.0,
                hourOfDay != null ? hourOfDay : 12,
                dayOfWeek != null ? dayOfWeek : 3
        };
    }
}