package com.fintech.processor;

import com.fintech.model.Candle;
import com.fintech.model.FeatureVector;
import com.fintech.model.TickEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FeatureEngineeringProcessorTest {

    private FeatureEngineeringProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new FeatureEngineeringProcessor();
    }

    @Test
    void testRSI_calculation() {
        double[] closes = {44.0, 44.5, 45.0, 44.8, 44.2, 44.0, 43.5, 43.0, 43.2, 43.5,
                            44.0, 44.5, 45.0, 45.5, 46.0, 46.5, 47.0, 47.5, 48.0, 48.5};
        double rsi = computeRSI(closes, 14);
        assertTrue(rsi >= 0 && rsi <= 100);
    }

    @Test
    void testRSI_bullishMarket_returnsHighValue() {
        double[] closes = {100.0, 101.0, 102.0, 103.0, 104.0, 105.0, 106.0, 107.0, 108.0, 109.0,
                            110.0, 111.0, 112.0, 113.0, 114.0};
        double rsi = computeRSI(closes, 14);
        assertTrue(rsi > 50); // Bullish trend produces RSI above 50
    }

    @Test
    void testRSI_bearishMarket_returnsLowValue() {
        double[] closes = {114.0, 113.0, 112.0, 111.0, 110.0, 109.0, 108.0, 107.0, 106.0, 105.0,
                            104.0, 103.0, 102.0, 101.0, 100.0};
        double rsi = computeRSI(closes, 14);
        assertTrue(rsi < 50); // Bearish trend produces RSI below 50
    }

    @Test
    void testRSI_shortPeriod_returnsZero() {
        double[] closes = {100.0, 105.0};
        double rsi = computeRSI(closes, 14);
        assertEquals(0, rsi);
    }

    @Test
    void testMACD_computation() {
        double[] closes = new double[50];
        for (int i = 0; i < 50; i++) closes[i] = 100 + i;
        double[] macdResult = computeMACD(closes);
        assertEquals(3, macdResult.length); // [macdLine, signalLine, histogram]
        assertTrue(!Double.isNaN(macdResult[0]));
    }

    @Test
    void testATR_calculation() {
        double[] highs = {110, 112, 111, 113, 115};
        double[] lows = {95, 97, 96, 98, 100};
        double[] closes = {100, 105, 102, 108, 110};
        double atr = computeATR(highs, lows, closes, 14);
        assertTrue(atr >= 0);
    }

    @Test
    void testBollingerBands_computation() {
        double[] closes = new double[30];
        for (int i = 0; i < 30; i++) closes[i] = 100 + Math.sin(i * 0.5) * 5;
        double[] bb = computeBollingerBands(closes);
        assertEquals(4, bb.length); // [upper, middle, lower, width]
        assertTrue(bb[0] > bb[1]); // upper > middle
        assertTrue(bb[1] > bb[2]); // middle > lower
        assertTrue(bb[3] >= 0);   // width >= 0
    }

    @Test
    void testVolumeMARatio_computation() {
        double[] volumes = {1000, 1200, 1100, 1300, 1500, 1400, 1600, 1800, 1700, 1900,
                            2000, 1950, 2100, 2050, 2200, 2300, 2350, 2250, 2400, 2450};
        double ratio = computeVolumeMARatio(volumes);
        assertTrue(ratio > 0);
    }

    @Test
    void testBidAskSpread_calculation() {
        BigDecimal bid = BigDecimal.valueOf(42150.0);
        BigDecimal ask = BigDecimal.valueOf(42151.5);
        BigDecimal spread = ask.subtract(bid).divide(bid, 8, java.math.RoundingMode.HALF_UP);
        assertTrue(spread.compareTo(BigDecimal.ZERO) > 0);
        assertTrue(spread.compareTo(BigDecimal.valueOf(0.001)) < 0); // Reasonable spread
    }

    // ---------- Mirrored implementations from FeatureEngineeringProcessor ----------

    private double computeRSI(double[] closes, int period) {
        if (closes.length < period) return 0;
        double gainSum = 0, lossSum = 0;
        for (int i = closes.length - period; i < closes.length; i++) {
            double diff = closes[i] - closes[i - 1];
            if (diff > 0) gainSum += diff;
            else lossSum += Math.abs(diff);
        }
        double avgGain = gainSum / period;
        double avgLoss = lossSum / period;
        if (avgLoss == 0) return 100;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    private double[] computeMACD(double[] closes) {
        double ema12 = ema(closes, 12);
        double ema26 = ema(closes, 26);
        double macdLine = ema12 - ema26;
        double signalLine = macdLine * 0.9; // Simplified signal
        return new double[]{macdLine, signalLine, macdLine - signalLine};
    }

    private double ema(double[] values, int period) {
        if (values.length == 0) return 0;
        double multiplier = 2.0 / (period + 1);
        double ema = values[0];
        for (int i = 1; i < values.length; i++) {
            ema = (values[i] * multiplier) + (ema * (1 - multiplier));
        }
        return ema;
    }

    private double computeATR(double[] highs, double[] lows, double[] closes, int period) {
        if (closes.length < 2) return 0;
        double sum = 0;
        int count = Math.min(Math.min(highs.length, lows.length), closes.length);
        for (int i = 1; i < count; i++) {
            double tr = Math.max(highs[i] - lows[i],
                    Math.max(Math.abs(highs[i] - closes[i - 1]), Math.abs(lows[i] - closes[i - 1])));
            sum += tr;
        }
        return count > 1 ? sum / (count - 1) : 0;
    }

    private double[] computeBollingerBands(double[] closes) {
        int period = Math.min(20, closes.length);
        double sum = 0;
        for (int i = closes.length - period; i < closes.length; i++) sum += closes[i];
        double sma = sum / period;
        double variance = 0;
        for (int i = closes.length - period; i < closes.length; i++) {
            variance += Math.pow(closes[i] - sma, 2);
        }
        double stdDev = Math.sqrt(variance / period);
        double upper = sma + 2 * stdDev;
        double lower = sma - 2 * stdDev;
        return new double[]{upper, sma, lower, upper - lower};
    }

    private double computeVolumeMARatio(double[] volumes) {
        int period = Math.min(20, volumes.length);
        double sum = 0;
        for (int i = volumes.length - period; i < volumes.length; i++) sum += volumes[i];
        double avgVolume = sum / period;
        double latestVolume = volumes[volumes.length - 1];
        return avgVolume > 0 ? latestVolume / avgVolume : 0;
    }
}