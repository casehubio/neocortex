package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class CalibrationDiagnosticsTest {

    @Test
    void perfectlyCalibrated_reliabilityNearZero() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(0.7, 0.3).build();
        var data = new ArrayList<Map<String, String>>();
        for (int i = 0; i < 700; i++) data.add(Map.of("X", "a"));
        for (int i = 0; i < 300; i++) data.add(Map.of("X", "b"));

        CalibrationReport report = CalibrationDiagnostics.calibrate(model, data);
        assertThat(report.reliability()).isCloseTo(0.0, within(1e-10));
    }

    @Test
    void overconfidentModel_highReliability() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(0.99, 0.01).build();
        var data = new ArrayList<Map<String, String>>();
        for (int i = 0; i < 50; i++) data.add(Map.of("X", "a"));
        for (int i = 0; i < 50; i++) data.add(Map.of("X", "b"));

        CalibrationReport report = CalibrationDiagnostics.calibrate(model, data);
        assertThat(report.reliability()).isGreaterThan(0.1);
    }

    @Test
    void brierDecomposition_sumsCorrectly() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(0.6, 0.4).build();
        var data = new ArrayList<Map<String, String>>();
        for (int i = 0; i < 60; i++) data.add(Map.of("X", "a"));
        for (int i = 0; i < 40; i++) data.add(Map.of("X", "b"));

        CalibrationReport report = CalibrationDiagnostics.calibrate(model, data);
        double reconstructed = report.reliability() - report.resolution() + report.uncertainty();
        assertThat(report.brierScore()).isCloseTo(reconstructed, within(1e-10));
    }

    @Test
    void perVariableReports_present() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("A", "a0", "a1").prior(0.5, 0.5)
            .variable("B", "b0", "b1").given("A").cpt(0.8, 0.2, 0.3, 0.7)
            .build();
        var data = List.of(
            Map.of("A", "a0", "B", "b0"),
            Map.of("A", "a1", "B", "b1"));

        CalibrationReport report = CalibrationDiagnostics.calibrate(model, data);
        assertThat(report.perVariable()).containsKeys("A", "B");
        assertThat(report.totalPredictions()).isEqualTo(8);
    }

    @Test
    void binCount_matchesTotalPredictions() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(0.5, 0.5).build();
        var data = new ArrayList<Map<String, String>>();
        for (int i = 0; i < 100; i++) data.add(Map.of("X", i % 2 == 0 ? "a" : "b"));

        CalibrationReport report = CalibrationDiagnostics.calibrate(model, data, 5);
        int totalInBins = report.aggregate().bins().stream()
            .mapToInt(CalibrationBin::count).sum();
        assertThat(totalInBins).isEqualTo(report.totalPredictions());
    }

    @Test
    void customBinCount() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(0.5, 0.5).build();
        var data = List.of(Map.of("X", "a"));

        CalibrationReport report = CalibrationDiagnostics.calibrate(model, data, 20);
        assertThat(report.aggregate().bins()).hasSize(20);
    }

    @Test
    void emptyData_throws() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(0.5, 0.5).build();
        assertThatThrownBy(() -> CalibrationDiagnostics.calibrate(model, List.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void brierScore_boundsCheck() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(0.5, 0.5).build();
        var data = List.of(Map.of("X", "a"), Map.of("X", "b"));

        CalibrationReport report = CalibrationDiagnostics.calibrate(model, data);
        assertThat(report.brierScore()).isBetween(0.0, 1.0);
    }

    @Test
    void conditionalModel_calibration() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("A", "a0", "a1").prior(0.5, 0.5)
            .variable("B", "b0", "b1").given("A").cpt(0.8, 0.2, 0.2, 0.8)
            .build();
        var data = new ArrayList<Map<String, String>>();
        for (int i = 0; i < 400; i++) data.add(Map.of("A", "a0", "B", "b0"));
        for (int i = 0; i < 100; i++) data.add(Map.of("A", "a0", "B", "b1"));
        for (int i = 0; i < 100; i++) data.add(Map.of("A", "a1", "B", "b0"));
        for (int i = 0; i < 400; i++) data.add(Map.of("A", "a1", "B", "b1"));

        CalibrationReport report = CalibrationDiagnostics.calibrate(model, data);
        assertThat(report.reliability()).isCloseTo(0.0, within(1e-10));
    }

    @Test
    void defaultBinCount_isTen() {
        BayesNetwork model = BayesNetworkBuilder.create()
            .variable("X", "a", "b").prior(0.5, 0.5).build();
        var data = List.of(Map.of("X", "a"));

        CalibrationReport report = CalibrationDiagnostics.calibrate(model, data);
        assertThat(report.aggregate().bins()).hasSize(10);
    }
}
