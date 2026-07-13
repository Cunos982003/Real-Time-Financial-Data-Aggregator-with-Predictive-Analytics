import java.io.*;
import java.util.*;

public class drift_calc {
    public static void main(String[] args) throws Exception {
        PrintWriter out = new PrintWriter("drift_result.txt");
        // testDetect_noDrift_usesThreshold
        List<Double> baseline = List.of(50.0, 45.0, 55.0, 40.0, 60.0);
        List<Double> current = List.of(49.0, 46.0, 54.0, 41.0, 59.0);

        double baselineMean = baseline.stream().mapToDouble(Double::doubleValue).average().orElse(50.0);
        double baselineVar = baseline.stream().mapToDouble(v -> Math.pow(v - baselineMean, 2)).average().orElse(1.0);
        double currentMean = current.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double currentVar = current.stream().mapToDouble(v -> Math.pow(v - currentMean, 2)).average().orElse(1e-6);

        double klDiv = computeKLDivergence(baselineMean, baselineVar, currentMean, currentVar);
        double psi = computePSI(baselineMean, baselineVar, currentMean, currentVar);

        out.println("=== testDetect_noDrift_usesThreshold ===");
        out.println("baselineMean=" + baselineMean + " baselineVar=" + baselineVar);
        out.println("currentMean=" + currentMean + " currentVar=" + currentVar);
        out.println("klDiv=" + klDiv + " (>1.0? " + (klDiv > 1.0) + ")");
        out.println("psi=" + psi + " (>0.1? " + (psi > 0.1) + ")");
        out.println("drift=" + (psi > 0.1 || klDiv > 1.0));
        out.println();

        // ModelTrainerTest.testDriftBaseline_initializedOnInit
        List<Double> baselineBTC = List.of(42000.0, 43000.0, 41000.0, 44000.0, 40000.0);
        List<Double> currentBTC = List.of(42000.0, 42500.0, 43000.0);

        double btcbMean = baselineBTC.stream().mapToDouble(Double::doubleValue).average().orElse(50.0);
        double btcbVar = baselineBTC.stream().mapToDouble(v -> Math.pow(v - btcbMean, 2)).average().orElse(1.0);
        double btccMean = currentBTC.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double btccVar = currentBTC.stream().mapToDouble(v -> Math.pow(v - btccMean, 2)).average().orElse(1e-6);

        double klBTC = computeKLDivergence(btcbMean, btcbVar, btccMean, btccVar);
        double psiBTC = computePSI(btcbMean, btcbVar, btccMean, btccVar);

        out.println("=== ModelTrainerTest.testDriftBaseline ===");
        out.println("baselineMean=" + btcbMean + " baselineVar=" + btcbVar);
        out.println("currentMean=" + btccMean + " currentVar=" + btccVar);
        out.println("klDiv=" + klBTC + " (>1.0? " + (klBTC > 1.0) + ")");
        out.println("psi=" + psiBTC + " (>0.1? " + (psiBTC > 0.1) + ")");
        out.println("drift=" + (psiBTC > 0.1 || klBTC > 1.0));
        out.close();
    }

    static double computeKLDivergence(double mu0, double sigma0, double mu1, double sigma1) {
        double var0 = Math.max(sigma0, 1e-6);
        double var1 = Math.max(sigma1, 1e-6);
        return 0.5 * (Math.log(var1 / var0) + (var0 + Math.pow(mu0 - mu1, 2)) / var1 - 1);
    }

    static double computePSI(double mu0, double sigma0, double mu1, double sigma1) {
        double std0 = Math.sqrt(Math.max(sigma0, 1e-6));
        double std1 = Math.sqrt(Math.max(sigma1, 1e-6));
        double ratio0 = mu0 / std0;
        double ratio1 = mu1 / std1;
        return Math.abs(ratio1 - ratio0) / (Math.abs(ratio0) > 1e-6 ? Math.abs(ratio0) : 1);
    }
}