package com.fintech.processor;

import com.fintech.model.Candle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class TickAggregationProcessor {

    private final ConcurrentMap<String, List<TickWindow>> windows = new ConcurrentHashMap<>();

    public void processTick(String symbol, BigDecimal price, BigDecimal volume,
                            BigDecimal bid, BigDecimal ask, Instant timestamp) {
        String windowKey = symbol + "_" + getWindowKey(timestamp);

        List<TickWindow> windowList = windows.computeIfAbsent(symbol, k -> new ArrayList<>());

        windowList.removeIf(w -> Duration.between(w.startTime, timestamp).getSeconds() > 60);

        TickWindow window = windowList.stream()
                .filter(w -> w.symbol.equals(symbol) && withinWindow(w, timestamp))
                .findFirst()
                .orElseGet(() -> createNewWindow(symbol, timestamp));

        if (window.tickCount == 0) {
            window.open = price;
            window.low = price;
            window.high = price;
        }
        window.high = window.high.max(price);
        window.low = window.low.min(price);
        window.close = price;
        window.volume = window.volume.add(volume != null ? volume : BigDecimal.ZERO);
        window.tickCount++;
    }

    public List<Candle> flushExpiredCandles(String symbol, Instant now) {
        List<TickWindow> symbolWindows = windows.getOrDefault(symbol, List.of());
        List<Candle> candles = new ArrayList<>();
        for (TickWindow w : symbolWindows) {
            if (Duration.between(w.startTime, now).getSeconds() >= 60) {
                candles.add(Candle.builder()
                        .symbol(symbol)
                        .timestamp(w.startTime)
                        .openTime(w.startTime)
                        .closeTime(w.startTime.plus(Duration.ofMinutes(1)))
                        .open(w.open.setScale(8, RoundingMode.HALF_UP))
                        .high(w.high.setScale(8, RoundingMode.HALF_UP))
                        .low(w.low.setScale(8, RoundingMode.HALF_UP))
                        .close(w.close.setScale(8, RoundingMode.HALF_UP))
                        .volume(w.volume.setScale(8, RoundingMode.HALF_UP))
                        .tickCount(w.tickCount)
                        .build());
            }
        }
        return candles;
    }

    private String getWindowKey(Instant timestamp) {
        long minute = timestamp.getEpochSecond() / 60;
        return String.valueOf(minute);
    }

    private boolean withinWindow(TickWindow w, Instant timestamp) {
        return !timestamp.isBefore(w.startTime) &&
                Duration.between(w.startTime, timestamp).getSeconds() < 60;
    }

    private TickWindow createNewWindow(String symbol, Instant timestamp) {
        long windowStart = (timestamp.getEpochSecond() / 60) * 60;
        Instant startTime = Instant.ofEpochSecond(windowStart);
        TickWindow w = new TickWindow(symbol, startTime);
        windows.computeIfAbsent(symbol, k -> new ArrayList<>()).add(w);
        return w;
    }

    private static class TickWindow {
        final String symbol;
        final Instant startTime;
        BigDecimal open = BigDecimal.ZERO;
        BigDecimal high = BigDecimal.ZERO;
        BigDecimal low = BigDecimal.ZERO;
        BigDecimal close = BigDecimal.ZERO;
        BigDecimal volume = BigDecimal.ZERO;
        int tickCount = 0;

        TickWindow(String symbol, Instant startTime) {
            this.symbol = symbol;
            this.startTime = startTime;
        }
    }
}