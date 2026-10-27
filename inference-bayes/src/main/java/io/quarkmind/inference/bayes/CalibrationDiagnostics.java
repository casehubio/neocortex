package io.quarkmind.inference.bayes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CalibrationDiagnostics {

    private CalibrationDiagnostics() {}

    public static CalibrationReport calibrate(BayesNetwork model,
                                               List<Map<String, String>> data) {
        return calibrate(model, data, 10);
    }

    public static CalibrationReport calibrate(BayesNetwork model,
                                               List<Map<String, String>> data,
                                               int numBins) {
        if (data.isEmpty()) throw new IllegalArgumentException("Data must not be empty");
        if (numBins < 1) throw new IllegalArgumentException("numBins must be >= 1: " + numBins);

        Map<String, ModelValidator.VarInfo> varInfos = ModelValidator.buildVarInfos(model);
        Map<String, List<double[]>> perVarPairs = new LinkedHashMap<>();
        List<double[]> allPairs = new ArrayList<>();

        for (Map<String, String> obs : data) {
            for (ModelValidator.VarInfo info : varInfos.values()) {
                String value = obs.get(info.name());
                if (value == null) continue;
                int observedIdx = ModelValidator.indexOf(info.outcomes(), value);
                if (observedIdx < 0) continue;
                int parentConfig = ModelValidator.parentConfigIndex(info, varInfos, obs);
                if (parentConfig < 0) continue;

                for (int outcomeIdx = 0; outcomeIdx < info.outcomes().length; outcomeIdx++) {
                    double predicted = info.cpt()[parentConfig][outcomeIdx];
                    double actual = (outcomeIdx == observedIdx) ? 1.0 : 0.0;
                    double[] pair = {predicted, actual};
                    allPairs.add(pair);
                    perVarPairs.computeIfAbsent(info.name(), k -> new ArrayList<>()).add(pair);
                }
            }
        }

        CalibrationCurve aggregate = buildCurve(allPairs, numBins);
        Map<String, CalibrationCurve> perVariable = new LinkedHashMap<>();
        for (var entry : perVarPairs.entrySet()) {
            perVariable.put(entry.getKey(), buildCurve(entry.getValue(), numBins));
        }

        double overallFreq = allPairs.isEmpty() ? 0.0 :
            allPairs.stream().mapToDouble(p -> p[1]).average().orElse(0.0);
        double reliability = computeReliability(aggregate.bins(), allPairs.size());
        double resolution = computeResolution(aggregate.bins(), overallFreq, allPairs.size());
        double uncertainty = overallFreq * (1 - overallFreq);

        return new CalibrationReport(
            aggregate.brierScore(), reliability, resolution, uncertainty,
            perVariable, aggregate, allPairs.size());
    }

    private static CalibrationCurve buildCurve(List<double[]> pairs, int numBins) {
        double binWidth = 1.0 / numBins;
        List<CalibrationBin> bins = new ArrayList<>();

        for (int i = 0; i < numBins; i++) {
            double lower = i * binWidth;
            double upper = (i == numBins - 1) ? 1.0 + 1e-10 : (i + 1) * binWidth;
            double sumPredicted = 0;
            double sumActual = 0;
            int count = 0;

            for (double[] pair : pairs) {
                if (pair[0] >= lower && pair[0] < upper) {
                    sumPredicted += pair[0];
                    sumActual += pair[1];
                    count++;
                }
            }

            double meanPred = count > 0 ? sumPredicted / count : (lower + Math.min(upper, 1.0)) / 2;
            double obsFrq = count > 0 ? sumActual / count : 0;
            bins.add(new CalibrationBin(lower, Math.min(upper, 1.0), meanPred, obsFrq, count));
        }

        double brier = 0;
        for (double[] pair : pairs) {
            double diff = pair[0] - pair[1];
            brier += diff * diff;
        }
        brier = pairs.isEmpty() ? 0.0 : brier / pairs.size();

        return new CalibrationCurve(bins, brier);
    }

    private static double computeReliability(List<CalibrationBin> bins, int total) {
        if (total == 0) return 0.0;
        double sum = 0;
        for (var bin : bins) {
            if (bin.count() > 0) {
                double diff = bin.meanPredicted() - bin.observedFrequency();
                sum += bin.count() * diff * diff;
            }
        }
        return sum / total;
    }

    private static double computeResolution(List<CalibrationBin> bins,
                                             double overallFreq, int total) {
        if (total == 0) return 0.0;
        double sum = 0;
        for (var bin : bins) {
            if (bin.count() > 0) {
                double diff = bin.observedFrequency() - overallFreq;
                sum += bin.count() * diff * diff;
            }
        }
        return sum / total;
    }
}
