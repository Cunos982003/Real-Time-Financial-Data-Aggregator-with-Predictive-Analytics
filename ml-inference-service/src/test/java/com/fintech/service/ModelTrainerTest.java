package com.fintech.service;

import com.fintech.model.ModelMetadata;
import com.fintech.model.TrainingData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModelTrainerTest {

    private ModelTrainer modelTrainer;
    private DriftDetector driftDetector;

    @BeforeEach
    void setUp() {
        driftDetector = new DriftDetector();
        modelTrainer = new ModelTrainer(driftDetector);
        modelTrainer.init();
    }

    @Test
    void testModelMetadataCreated_forEachSymbol() {
        for (String sym : List.of("BTCUSD", "ETHUSD", "XRPUSD")) {
            ModelMetadata meta = modelTrainer.getModelMetadata(sym);
            assertNotNull(meta);
            assertEquals("mlp", meta.getModelType());
            assertEquals("READY", meta.getStatus());
        }
    }

    @Test
    void testTrain_createsModelAndReturnsMetadata() {
        TrainingData data = buildTrainingData("BTCUSD", 50);
        ModelMetadata result = modelTrainer.train("BTCUSD", data);

        assertNotNull(result);
        assertNotNull(result.getModelVersion());
        assertEquals("BTCUSD", result.getSymbol());
        assertEquals("mlp", result.getModelType());
        assertNotNull(modelTrainer.getModelMetadata("BTCUSD"));
    }

    @Test
    void testPredict_returnsClassLabel() {
        int prediction = modelTrainer.predict("BTCUSD", new double[15]);
        assertTrue(prediction == 0 || prediction == 1);
    }

    @Test
    void testPredictProbabilities_returnsValidDistribution() {
        double[] probs = modelTrainer.predictProbabilities("BTCUSD", new double[15]);
        assertEquals(2, probs.length);
        assertTrue(probs[0] >= 0 && probs[0] <= 1);
        assertTrue(probs[1] >= 0 && probs[1] <= 1);
    }

    @Test
    void testDriftBaseline_initializedOnInit() {
        var result = driftDetector.detect("BTCUSD_price",
                List.of(42000.0, 42500.0, 43000.0));
        assertNotNull(result);
        assertFalse(result.isDriftDetected());
    }

    private TrainingData buildTrainingData(String symbol, int points) {
        java.util.Random rand = new java.util.Random(symbol.hashCode());
        List<double[]> features = new java.util.ArrayList<>();
        List<Integer> labels = new java.util.ArrayList<>();
        for (int i = 0; i < points; i++) {
            double[] fv = new double[15];
            for (int j = 0; j < 15; j++) fv[j] = rand.nextDouble() * 10;
            features.add(fv);
            labels.add(rand.nextDouble() > 0.5 ? 1 : 0);
        }
        return TrainingData.builder().symbol(symbol).features(features).labels(labels).build();
    }
}