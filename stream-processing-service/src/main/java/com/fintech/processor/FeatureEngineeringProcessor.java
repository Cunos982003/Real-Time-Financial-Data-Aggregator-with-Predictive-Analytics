package com.fintech.processor;

import com.fintech.model.Candle;
import com.fintech.model.FeatureVector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeatureEngineeringProcessor {

    private static final int RSI_PERIOD = 14;
    private static final int MACD_FAST = 12;
    private static final int MACD_SLOW = 26;
    private static final int MACD_SIGNAL = 9;
    private static final int ATR_PERIOD = 14;
    private static final int BB_PERIOD = 20;
    private static final double BB_STD_MULT = 2.0;
    private static final int VOLUME_MA_PERIOD = 20;

    private final Map<String, Deque<Candle>> candleHistory = new ConcurrentHashMap<>();
    private final Map<String, Deque<Double>> rsiGains = new ConcurrentHashMap<>();
    private final Map<String, Deque<Double>> rsiLosses = new ConcurrentHashMap<>();
    private final Map<String, List<Double>> emaFast = new ConcurrentHashMap<>();
    private final Map<String, List<Double>> emaSlow = new ConcurrentHashMap<>();
    private final Map<String, List<Double>> emaSignal = new ConcurrentHashMap<>();
    private final Map<String, Deque<Double>> volumeHistory = new ConcurrentHashMap<>();

    public FeatureVector computeFeatures(String symbol, Candle candle, BigDecimal currentPrice,
                                        BigDecimal bid, BigDecimal ask) {

        Deque<Candle> history = candleHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        history.addLast(candle);
        while (history.size() > MACD_SLOW + 1) {
            history.removeFirst();
        }

        if (history.size() < MACD_SLOW + 1) {
            return buildBasicFeatures(symbol, candle, currentPrice, bid, ask);
        }

        List<BigDecimal> closes = history.stream()
                .map(Candle::getClose)
                .toList();

        BigDecimal rsi = computeRSI(symbol, closes);
        double[] macdResult = computeMACD(closes);
        BigDecimal[] bbResult = computeBollingerBands(closes);
        BigDecimal atr = computeATR(symbol, history);
        BigDecimal volumeMaRatio = computeVolumeMARatio(symbol, candle.getVolume());
        BigDecimal momentum = computeMomentum(closes);
        BigDecimal roc = computeROC(closes);
        BigDecimal spread = bid.compareTo(BigDecimal.ZERO) > 0 ?
                ask.subtract(bid).divide(bid, 8, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        return FeatureVector.builder()
                .symbol(symbol)
                .timestamp(candle.getTimestamp() != null ? candle.getTimestamp() : candle.getOpenTime())
                .rsi14(rsi)
                .macd(BigDecimal.valueOf(macdResult[0]))
                .macdSignal(BigDecimal.valueOf(macdResult[1]))
                .macdHistogram(BigDecimal.valueOf(macdResult[2]))
                .volatilityStd(bbResult[0])
                .bbUpper(bbResult[1])
                .bbLower(bbResult[2])
                .bbWidth(bbResult[1].subtract(bbResult[2]))
                .atr14(atr)
                .volumeMaRatio(volumeMaRatio)
                .priceMomentum(momentum)
                .rateOfChange(roc)
                .bidAskSpread(spread)
                .volumeImbalance(BigDecimal.ZERO)
                .price(currentPrice)
                .volume(candle.getVolume())
                .hourOfDay(candle.getOpenTime().atZone(ZoneOffset.UTC).getHour())
                .dayOfWeek(candle.getOpenTime().atZone(ZoneOffset.UTC).getDayOfWeek().getValue())
                .build();
    }

    private BigDecimal computeRSI(String symbol, List<BigDecimal> closes) {
        if (closes.size() < RSI_PERIOD + 1) return BigDecimal.valueOf(50);

        Deque<Double> gains = rsiGains.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> losses = rsiLosses.computeIfAbsent(symbol, k -> new ArrayDeque<>());

        double gain = 0, loss = 0;
        for (int i = closes.size() - RSI_PERIOD; i < closes.size(); i++) {
            double diff = closes.get(i).subtract(closes.get(i - 1)).doubleValue();
            if (diff > 0) gain += diff;
            else loss -= diff;
        }
        double avgGain = gain / RSI_PERIOD;
        double avgLoss = loss / RSI_PERIOD;
        double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
        return BigDecimal.valueOf(100 - (100 / (1 + rs)));
    }

    private double[] computeMACD(List<BigDecimal> closes) {
        String sym = closes.hashCode() + "";
        List<Double> fastEma = emaFast.computeIfAbsent(sym, k -> new ArrayList<>());
        List<Double> slowEma = emaSlow.computeIfAbsent(sym, k -> new ArrayList<>());
        List<Double> signalEma = emaSignal.computeIfAbsent(sym, k -> new ArrayList<>());

        List<Double> closeVals = closes.stream().map(BigDecimal::doubleValue).toList();

        if (fastEma.isEmpty()) {
            fastEma.addAll(calculateEMA(closeVals, MACD_FAST));
            slowEma.addAll(calculateEMA(closeVals, MACD_SLOW));
        }

        if (fastEma.isEmpty() || slowEma.isEmpty()) {
            return new double[]{0, 0, 0};
        }

        double fast = fastEma.get(fastEma.size() - 1);
        double slow = slowEma.get(slowEma.size() - 1);
        double macdLine = fast - slow;
        double signal = macdLine;
        if (!signalEma.isEmpty()) {
            double prevSignal = signalEma.get(signalEma.size() - 1);
            signal = (prevSignal * (MACD_SIGNAL - 1) + macdLine) / MACD_SIGNAL;
        }
        signalEma.add(signal);

        return new double[]{macdLine, signal, macdLine - signal};
    }

    private List<Double> calculateEMA(List<Double> values, int period) {
        if (values.size() < period) return List.of();
        double multiplier = 2.0 / (period + 1);
        List<Double> ema = new ArrayList<>();
        double sma = values.subList(0, period).stream().mapToDouble(Double::doubleValue).average().orElse(0);
        ema.add(sma);
        for (int i = period; i < values.size(); i++) {
            double prevEma = ema.get(ema.size() - 1);
            ema.add((values.get(i) - prevEma) * multiplier + prevEma);
        }
        return ema;
    }

    private BigDecimal[] computeBollingerBands(List<BigDecimal> closes) {
        int n = Math.min(closes.size(), BB_PERIOD);
        List<BigDecimal> recent = closes.subList(closes.size() - n, closes.size());
        double mean = recent.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double variance = recent.stream()
                .mapToDouble(c -> Math.pow(c.doubleValue() - mean, 2))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);
        double sma = mean;
        return new BigDecimal[]{
                BigDecimal.valueOf(stdDev),
                BigDecimal.valueOf(sma + BB_STD_MULT * stdDev),
                BigDecimal.valueOf(sma - BB_STD_MULT * stdDev)
        };
    }

    private BigDecimal computeATR(String symbol, Deque<Candle> candles) {
        if (candles.size() < ATR_PERIOD + 1) return BigDecimal.ZERO;

        List<Double> trs = new ArrayList<>();
        List<Candle> candleList = new ArrayList<>(candles);
        for (int i = candleList.size() - ATR_PERIOD; i < candleList.size(); i++) {
            double high = candleList.get(i).getHigh().doubleValue();
            double low = candleList.get(i).getLow().doubleValue();
            double prevClose = candleList.get(i - 1).getClose().doubleValue();
            double tr = Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
            trs.add(tr);
        }
        double atr = trs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return BigDecimal.valueOf(atr);
    }

    private BigDecimal computeVolumeMARatio(String symbol, BigDecimal currentVolume) {
        Deque<Double> volHistory = volumeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        volHistory.addLast(currentVolume.doubleValue());
        while (volHistory.size() > VOLUME_MA_PERIOD) {
            volHistory.removeFirst();
        }
        if (volHistory.size() < VOLUME_MA_PERIOD) return BigDecimal.ONE;
        double ma = volHistory.stream().mapToDouble(Double::doubleValue).average().orElse(1);
        return BigDecimal.valueOf(currentVolume.doubleValue() / ma);
    }

    private BigDecimal computeMomentum(List<BigDecimal> closes) {
        if (closes.size() < 10) return BigDecimal.ZERO;
        BigDecimal current = closes.get(closes.size() - 1);
        BigDecimal prior = closes.get(closes.size() - 10);
        return prior.compareTo(BigDecimal.ZERO) > 0 ?
                current.subtract(prior).divide(prior, 8, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    private BigDecimal computeROC(List<BigDecimal> closes) {
        if (closes.size() < 12) return BigDecimal.ZERO;
        BigDecimal current = closes.get(closes.size() - 1);
        BigDecimal prior = closes.get(closes.size() - 12);
        return prior.compareTo(BigDecimal.ZERO) > 0 ?
                current.subtract(prior).divide(prior, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
    }

    private FeatureVector buildBasicFeatures(String symbol, Candle candle, BigDecimal price,
                                              BigDecimal bid, BigDecimal ask) {
        return FeatureVector.builder()
                .symbol(symbol)
                .timestamp(candle.getOpenTime())
                .price(price)
                .volume(candle.getVolume())
                .bidAskSpread(BigDecimal.valueOf(0.0001))
                .rsi14(BigDecimal.valueOf(50))
                .macd(BigDecimal.ZERO)
                .volatilityStd(BigDecimal.ZERO)
                .bbWidth(BigDecimal.ZERO)
                .volumeMaRatio(BigDecimal.ONE)
                .hourOfDay(candle.getOpenTime().atZone(ZoneOffset.UTC).getHour())
                .dayOfWeek(candle.getOpenTime().atZone(ZoneOffset.UTC).getDayOfWeek().getValue())
                .build();
    }
}