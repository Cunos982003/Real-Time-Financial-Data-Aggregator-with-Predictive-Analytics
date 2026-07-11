package com.fintech.service;

import com.fintech.model.ModelMetadata;
import com.fintech.model.TrainingData;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelTrainer {

    private static final int INPUT_SIZE = 15;
    private static final int HIDDEN_SIZE = 32;
    private static final int OUTPUT_SIZE = 2;
    private static final double LEARNING_RATE = 0.01;

    private final Map<String, double[]> modelWeights = new HashMap<>();
    private final Map<String, double[]> modelBias = new HashMap<>();
    private final Map<String, ModelMetadata> modelMetadata = new HashMap<>();
    private final Map<String, double[]> scalerParams = new HashMap<>();

    private final Path modelStoragePath = Paths.get(System.getenv().getOrDefault("MODEL_STORAGE_PATH", "/models"));

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(modelStoragePath);
        } catch (IOException e) {
            log.warn("Could not create model storage directory: {}", e.getMessage());
        }
        String[] symbols = {"BTCUSD", "ETHUSD", "XRPUSD"};
        for (String sym : symbols) {
            modelWeights.put(sym, initializeWeights(INPUT_SIZE, HIDDEN_SIZE));
            modelBias.put(sym, new double[HIDDEN_SIZE]);
            scalerParams.put(sym, new double[INPUT_SIZE * 2]);
            modelMetadata.put(sym, ModelMetadata.builder()
                    .symbol(sym)
                    .modelVersion(Instant.now().toString())
                    .modelType("mlp")
                    .featureCount(INPUT_SIZE)
                    .trainedAt(Instant.now())
                    .status("READY")
                    .build());
        }
    }

    public ModelMetadata train(String symbol, TrainingData data) {
        log.info("Training model for {} with {} samples", symbol, data.size());
        long start = System.currentTimeMillis();

        double[][] featureArray = data.getFeatures().toArray(new double[0][]);
        double[][] normalizedFeatures = normalize(featureArray, symbol);

        int splitIndex = (int) (normalizedFeatures.length * 0.8);
        double[][] trainFeatures = Arrays.copyOfRange(normalizedFeatures, 0, splitIndex);
        int[] trainLabels = data.getLabels().stream().mapToInt(Integer::intValue).toArray();

        double[] weights = initializeWeights(INPUT_SIZE, HIDDEN_SIZE);
        double[] bias = new double[HIDDEN_SIZE];

        for (int epoch = 0; epoch < 100; epoch++) {
            for (int i = 0; i < trainFeatures.length; i++) {
                double[] features = trainFeatures[i];
                int label = trainLabels[i];

                double[] hiddenLayer = forwardHidden(features, weights, bias);
                double[] output = softmax(hiddenLayer, weights);

                double[] outputError = output.clone();
                outputError[label] -= 1.0;
                double[] hiddenGrad = new double[HIDDEN_SIZE];
                for (int j = 0; j < HIDDEN_SIZE; j++) {
                    hiddenGrad[j] = outputError[0] * weights[j] / HIDDEN_SIZE + outputError[1] * weights[HIDDEN_SIZE + j] / HIDDEN_SIZE;
                }
                for (int j = 0; j < HIDDEN_SIZE; j++) {
                    for (int k = 0; k < INPUT_SIZE; k++) {
                        weights[j * INPUT_SIZE + k] -= LEARNING_RATE * hiddenGrad[j] * features[k];
                    }
                    bias[j] -= LEARNING_RATE * hiddenGrad[j];
                }
            }
        }

        double accuracy = evaluate(trainFeatures, trainLabels, weights, bias);
        log.info("Training complete for {} in {}ms, accuracy={}", symbol, System.currentTimeMillis() - start, accuracy);

        modelWeights.put(symbol, weights);
        modelBias.put(symbol, bias);

        String version = Instant.now().toString();
        ModelMetadata meta = ModelMetadata.builder()
                .symbol(symbol)
                .modelVersion(version)
                .modelType("mlp")
                .trainedAt(Instant.now())
                .lastRetrainingAt(Instant.now())
                .featureCount(INPUT_SIZE)
                .trainSamples(trainFeatures.length)
                .testSamples(normalizedFeatures.length - splitIndex)
                .status("READY")
                .build();
        modelMetadata.put(symbol, meta);
        saveModel(symbol, weights, bias, meta);
        return meta;
    }

    public int predict(String symbol, double[] features) {
        double[] w = modelWeights.get(symbol);
        double[] b = modelBias.get(symbol);
        if (w == null || b == null) return 0;

        double[] normalized = normalize(features, symbol);
        double[] hidden = forwardHidden(normalized, w, b);
        double[] output = softmax(hidden, w);
        return output[0] > output[1] ? 0 : 1;
    }

    public double[] predictProbabilities(String symbol, double[] features) {
        double[] w = modelWeights.get(symbol);
        double[] b = modelBias.get(symbol);
        if (w == null || b == null) return new double[]{0.5, 0.5};

        double[] normalized = normalize(features, symbol);
        double[] hidden = forwardHidden(normalized, w, b);
        return softmax(hidden, w);
    }

    private double[] forwardHidden(double[] input, double[] w, double[] b) {
        double[] hidden = new double[HIDDEN_SIZE];
        for (int j = 0; j < HIDDEN_SIZE; j++) {
            double sum = b[j];
            for (int i = 0; i < INPUT_SIZE; i++) {
                sum += input[i] * w[j * INPUT_SIZE + i];
            }
            hidden[j] = Math.max(0, sum);
        }
        return hidden;
    }

    private double[] softmax(double[] hidden, double[] weights) {
        double max = Arrays.stream(hidden).max().orElse(0);
        double[] exp = new double[OUTPUT_SIZE];
        double total = 0;
        for (int j = 0; j < HIDDEN_SIZE; j++) {
            exp[0] += Math.exp(hidden[j] * weights[j * INPUT_SIZE] / 10.0);
            exp[1] += Math.exp(hidden[j] * weights[j * INPUT_SIZE + 1] / 10.0);
        }
        total = exp[0] + exp[1];
        if (total > 0) {
            exp[0] /= total;
            exp[1] /= total;
        }
        return exp;
    }

    private double evaluate(double[][] features, int[] labels, double[] w, double[] b) {
        int correct = 0;
        for (int i = 0; i < features.length; i++) {
            double[] hidden = forwardHidden(features[i], w, b);
            int pred = hidden[0] > hidden[1] ? 0 : 1;
            if (pred == labels[i]) correct++;
        }
        return (double) correct / features.length;
    }

    private double[][] normalize(double[][] features, String symbol) {
        double[] sp = scalerParams.computeIfAbsent(symbol, k -> new double[INPUT_SIZE * 2]);
        double[][] result = new double[features.length][INPUT_SIZE];
        for (int f = 0; f < INPUT_SIZE && f < features[0].length; f++) {
            double min = Double.MAX_VALUE, max = Double.MIN_VALUE;
            for (double[] row : features) {
                if (f < row.length) {
                    min = Math.min(min, row[f]);
                    max = Math.max(max, row[f]);
                }
            }
            sp[f] = min;
            sp[INPUT_SIZE + f] = max == min ? 1 : max - min;
        }
        for (int i = 0; i < features.length; i++) {
            for (int f = 0; f < INPUT_SIZE && f < features[i].length; f++) {
                result[i][f] = sp[INPUT_SIZE + f] == 0 ? 0 :
                        (features[i][f] - sp[f]) / sp[INPUT_SIZE + f];
            }
        }
        return result;
    }

    private double[] normalize(double[] features, String symbol) {
        double[] sp = scalerParams.computeIfAbsent(symbol, k -> new double[INPUT_SIZE * 2]);
        double[] result = new double[INPUT_SIZE];
        for (int f = 0; f < INPUT_SIZE && f < features.length; f++) {
            result[f] = sp[INPUT_SIZE + f] == 0 ? 0 : (features[f] - sp[f]) / sp[INPUT_SIZE + f];
        }
        return result;
    }

    private double[] initializeWeights(int rows, int cols) {
        double scale = Math.sqrt(2.0 / rows);
        Random rand = new Random(42);
        double[] w = new double[rows * cols];
        for (int i = 0; i < w.length; i++) {
            w[i] = (rand.nextDouble() * 2 - 1) * scale;
        }
        return w;
    }

    private void saveModel(String symbol, double[] weights, double[] bias, ModelMetadata meta) {
        try {
            Path symbolDir = modelStoragePath.resolve(symbol);
            Files.createDirectories(symbolDir);
            Path versionDir = symbolDir.resolve(meta.getModelVersion());
            Files.createDirectories(versionDir);

            try (DataOutputStream wOut = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(versionDir.resolve("weights.bin"))))) {
                for (double v : weights) wOut.writeDouble(v);
            }
            try (DataOutputStream bOut = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(versionDir.resolve("bias.bin"))))) {
                for (double v : bias) bOut.writeDouble(v);
            }
            Files.writeString(versionDir.resolve("metadata.json"),
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(meta));
            log.info("Model saved for {} at {}", symbol, versionDir);
        } catch (Exception e) {
            log.error("Failed to save model for {}: {}", symbol, e.getMessage());
        }
    }

    public ModelMetadata getModelMetadata(String symbol) {
        return modelMetadata.get(symbol);
    }
}