package com.fintech.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DriftDetectorTest {

    private DriftDetector driftDetector;

    @BeforeEach
    void setUp() {
        driftDetector = new DriftDetector();
        driftDetector.setBaseline("test_rsi", List.of(50.0, 45.0, 55.0, 40.0, 60.0));
    }

    @Test
    void testComputeKLDivergence_sameDistribution_returnsZero() {
        var result = driftDetector.detect("test_rsi", List.of(50.0, 45.0, 55.0, 40.0, 60.0));
        assertNotNull(result);
        assertFalse(result.isDriftDetected());
    }

    @Test
    void testDetect_largeDrift_returnsDriftDetected() {
        // Shift distribution significantly (e.g., from ~50 to ~90)
        var result = driftDetector.detect("test_rsi", List.of(90.0, 95.0, 85.0, 92.0, 88.0));
        assertTrue(result.isDriftDetected());
        assertEquals("DRIFT_DETECTED", result.getStatus());
    }

    @Test
    void testDetect_noDrift_usesThreshold() {
        var result = driftDetector.detect("test_rsi", List.of(49.0, 46.0, 54.0, 41.0, 59.0));
        assertFalse(result.isDriftDetected()); // Within threshold
    }

    @Test
    void testDetect_emptyValues_returnsNoDrift() {
        var result = driftDetector.detect("test_rsi", List.of());
        assertFalse(result.isDriftDetected());
    }

    @Test
    void testDetect_nullValues_returnsNoDrift() {
        var result = driftDetector.detect("test_rsi", null);
        assertFalse(result.isDriftDetected());
    }

    @Test
    void testGetLastResult_returnsCachedResult() {
        driftDetector.detect("test_rsi", List.of(50.0, 45.0));
        var last = driftDetector.getLastResult("test_rsi");
        assertNotNull(last);
    }
}