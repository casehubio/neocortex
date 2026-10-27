package io.quarkmind.inference.bayes;

import java.util.Map;

public record CalibrationReport(
    double brierScore,
    double reliability,
    double resolution,
    double uncertainty,
    Map<String, CalibrationCurve> perVariable,
    CalibrationCurve aggregate,
    int totalPredictions
) {
    public CalibrationReport {
        perVariable = Map.copyOf(perVariable);
    }
}
