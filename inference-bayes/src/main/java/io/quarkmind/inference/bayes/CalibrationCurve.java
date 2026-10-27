package io.quarkmind.inference.bayes;

import java.util.List;

public record CalibrationCurve(
    List<CalibrationBin> bins,
    double brierScore
) {
    public CalibrationCurve {
        bins = List.copyOf(bins);
    }
}
