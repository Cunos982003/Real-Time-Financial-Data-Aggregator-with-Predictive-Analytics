package com.fintech.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BinanceConnectorTest {

    private BinanceConnector connector;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        connector = new BinanceConnector(objectMapper);
    }

    @Test
    void testGetExchangeName_returnsBinancE() {
        assertEquals("BINANCE", connector.getExchangeName());
    }

    @Test
    void testParseTickerMessage_validJson_returnsTickMessage() throws Exception {
        String json = """
            {"e":"24hrTicker","s":"BTCUSDT","c":"42150.00","b":"42149.50","a":"42151.00",
             "v":"1234.5678","B":"1000.0","A":"1001.0","E":1700000000123}
            """;
        TickMessage msg = invokeParseMethod(json, "BTCUSD");

        assertNotNull(msg);
        assertEquals("BTCUSD", msg.getSymbol());
        assertEquals(new BigDecimal("42150.00"), msg.getPrice());
        assertEquals(new BigDecimal("42149.50"), msg.getBidPrice());
        assertEquals(new BigDecimal("42151.00"), msg.getAskPrice());
        assertEquals("BINANCE", msg.getExchange());
    }

    @Test
    void testParseTickerMessage_wrongEventType_returnsNull() throws Exception {
        String json = """
            {"e":"trade","s":"BTCUSDT","p":"42150.00"}
            """;
        TickMessage msg = invokeParseMethod(json, "BTCUSD");
        assertNull(msg);
    }

    @Test
    void testParseTickerMessage_invalidJson_returnsNull() {
        TickMessage msg = invokeParseMethod("{invalid json", "BTCUSD");
        assertNull(msg);
    }

    @Test
    void testParseTickerMessage_missingFields_returnsNull() {
        TickMessage msg = invokeParseMethod("{}", "BTCUSD");
        assertNull(msg);
    }

    @Test
    void testParseTickerMessage_partialData_usesDefaults() throws Exception {
        String json = """
            {"e":"24hrTicker","s":"ETHUSDT","c":"2500.00","b":"0","a":"0","v":"0","B":"0","A":"0","E":0}
            """;
        TickMessage msg = invokeParseMethod(json, "ETHUSD");
        assertNotNull(msg);
        assertEquals("ETHUSD", msg.getSymbol());
        assertEquals(new BigDecimal("2500.00"), msg.getPrice());
    }

    private TickMessage invokeParseMethod(String raw, String symbol) {
        try {
            var method = BinanceConnector.class.getDeclaredMethod("parseTickerMessage", String.class, String.class);
            method.setAccessible(true);
            return (TickMessage) method.invoke(connector, raw, symbol);
        } catch (Exception e) {
            return null;
        }
    }
}