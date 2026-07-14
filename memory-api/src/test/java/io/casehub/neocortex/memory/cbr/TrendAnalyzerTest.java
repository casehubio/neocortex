package io.casehub.neocortex.memory.cbr;

import org.junit.jupiter.api.Test;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.casehub.neocortex.memory.MemoryDomain;

import static io.casehub.neocortex.memory.cbr.FeatureValue.number;
import static io.casehub.neocortex.memory.cbr.FeatureValue.string;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TrendAnalyzerTest {

    private static final MemoryDomain CBR = new MemoryDomain("cbr");

    private static final FeatureField.TimeSeries SCHEMA =
            (FeatureField.TimeSeries) FeatureField.timeSeries("vitals", "t",
                    null,
                    new TrendSpec(Set.of(TrendType.SLOPE, TrendType.DELTA, TrendType.VOLATILITY,
                            TrendType.ACCELERATION, TrendType.CHANGE_POINTS,
                            TrendType.DURATION, TrendType.OBSERVATION_COUNT), ChronoUnit.HOURS),
                    FeatureField.numeric("t", 0, 100),
                    FeatureField.numeric("hr", 40, 200));

    // -- SLOPE --

    @Test
    void slope_linearIncreasing() {
        var obs = List.of(
                Map.<String, FeatureValue>of("t", number(0), "hr", number(60)),
                Map.<String, FeatureValue>of("t", number(1), "hr", number(70)),
                Map.<String, FeatureValue>of("t", number(2), "hr", number(80)));
        var profile = TrendAnalyzer.analyze(obs, SCHEMA);
        assertThat(profile.metrics().get("vitals_slope_hr")).isCloseTo(10.0, within(0.001));
    }

    @Test
    void slope_linearDecreasing() {
        var obs = List.of(
                Map.<String, FeatureValue>of("t", number(0), "hr", number(100)),
                Map.<String, FeatureValue>of("t", number(2), "hr", number(80)));
        var profile = TrendAnalyzer.analyze(obs, SCHEMA);
        assertThat(profile.metrics().get("vitals_slope_hr")).isCloseTo(-10.0, within(0.001));
    }

    @Test
    void slope_singleObservation_zero() {
        var obs = List.of(Map.<String, FeatureValue>of("t", number(0), "hr", number(60)));
        var profile = TrendAnalyzer.analyze(obs, SCHEMA);
        assertThat(profile.metrics().get("vitals_slope_hr")).isEqualTo(0.0);
    }

    @Test
    void slope_emptyObservations_zero() {
        var profile = TrendAnalyzer.analyze(List.of(), SCHEMA);
        assertThat(profile.metrics().get("vitals_slope_hr")).isEqualTo(0.0);
    }

    @Test
    void slope_constantValues_zero() {
        var obs = List.of(
                Map.<String, FeatureValue>of("t", number(0), "hr", number(80)),
                Map.<String, FeatureValue>of("t", number(1), "hr", number(80)),
                Map.<String, FeatureValue>of("t", number(2), "hr", number(80)));
        var profile = TrendAnalyzer.analyze(obs, SCHEMA);
        assertThat(profile.metrics().get("vitals_slope_hr")).isCloseTo(0.0, within(0.001));
    }

    // -- DELTA --

    @Test
    void delta_increasingSequence() {
        var obs = List.of(
                Map.<String, FeatureValue>of("t", number(0), "hr", number(60)),
                Map.<String, FeatureValue>of("t", number(1), "hr", number(75)),
                Map.<String, FeatureValue>of("t", number(2), "hr", number(90)));
        var profile = TrendAnalyzer.analyze(obs, SCHEMA);
        assertThat(profile.metrics().get("vitals_delta_hr")).isEqualTo(30.0);
    }

    @Test
    void delta_singleObservation_zero() {
        var obs = List.of(Map.<String, FeatureValue>of("t", number(0), "hr", number(60)));
        var profile = TrendAnalyzer.analyze(obs, SCHEMA);
        assertThat(profile.metrics().get("vitals_delta_hr")).isEqualTo(0.0);
    }

    // -- VOLATILITY --

    @Test
    void volatility_constantValues_zero() {
        var obs = List.of(
                Map.<String, FeatureValue>of("t", number(0), "hr", number(80)),
                Map.<String, FeatureValue>of("t", number(1), "hr", number(80)),
                Map.<String, FeatureValue>of("t", number(2), "hr", number(80)));
        var profile = TrendAnalyzer.analyze(obs, SCHEMA);
        assertThat(profile.metrics().get("vitals_volatility_hr")).isEqualTo(0.0);
    }

    @Test
    void volatility_knownValues() {
        // values: 60, 80, 100 → mean=80, pop stddev = sqrt((400+0+400)/3) = sqrt(800/3) ≈ 16.33
        var obs = List.of(
                Map.<String, FeatureValue>of("t", number(0), "hr", number(60)),
                Map.<String, FeatureValue>of("t", number(1), "hr", number(80)),
                Map.<String, FeatureValue>of("t", number(2), "hr", number(100)));
        var profile = TrendAnalyzer.analyze(obs, SCHEMA);
        assertThat(profile.metrics().get("vitals_volatility_hr"))
                .isCloseTo(Math.sqrt(800.0 / 3), within(0.01));
    }

    @Test
    void volatility_singleObservation_zero() {
        var obs = List.of(Map.<String, FeatureValue>of("t", number(0), "hr", number(60)));
        var profile = TrendAnalyzer.analyze(obs, SCHEMA);
        assertThat(profile.metrics().get("vitals_volatility_hr")).isEqualTo(0.0);
    }

    // -- ACCELERATION --

    @Test
    void acceleration_constantSlope_zero() {
        // Linear: 60, 70, 80, 90 → slope first half = slope second half → accel = 0
        var obs = List.of(
                Map.<String, FeatureValue>of("t", number(0), "hr", number(60)),
                Map.<String, FeatureValue>of("t", number(1), "hr", number(70)),
                Map.<String, FeatureValue>of("t", number(2), "hr", number(80)),
                Map.<String, FeatureValue>of("t", number(3), "hr", number(90)));
        var profile = TrendAnalyzer.analyze(obs, SCHEMA);
        assertThat(profile.metrics().get("vitals_acceleration_hr")).isCloseTo(0.0, within(0.01));
    }

    @Test
    void acceleration_accelerating_positive() {
        // First half: 60→65 (slope 5/hr), second half: 80→100 (slope 20/hr) → positive accel
        var obs = List.of(
                Map.<String, FeatureValue>of("t", number(0), "hr", number(60)),
                Map.<String, FeatureValue>of("t", number(1), "hr", number(65)),
                Map.<String, FeatureValue>of("t", number(2), "hr", number(80)),
                Map.<String, FeatureValue>of("t", number(3), "hr", number(100)));
        var profile = TrendAnalyzer.analyze(obs, SCHEMA);
        assertThat(profile.metrics().get("vitals_acceleration_hr")).isGreaterThan(0.0);
    }

    @Test
    void acceleration_fewerThanFour_zero() {
        var obs = List.of(
                Map.<String, FeatureValue>of("t", number(0), "hr", number(60)),
                Map.<String, FeatureValue>of("t", number(1), "hr", number(70)),
                Map.<String, FeatureValue>of("t", number(2), "hr", number(80)));
        var profile = TrendAnalyzer.analyze(obs, SCHEMA);
        assertThat(profile.metrics().get("vitals_acceleration_hr")).isEqualTo(0.0);
    }

    // -- CHANGE_POINTS --

    @Test
    void changePoints_steadySequence_zero() {
        var obs = List.of(
                Map.<String, FeatureValue>of("t", number(0), "hr", number(80)),
                Map.<String, FeatureValue>of("t", number(1), "hr", number(81)),
                Map.<String, FeatureValue>of("t", number(2), "hr", number(80)),
                Map.<String, FeatureValue>of("t", number(3), "hr", number(79)));
        var profile = TrendAnalyzer.analyze(obs, SCHEMA);
        assertThat(profile.metrics().get("vitals_change_points_hr")).isEqualTo(0.0);
    }

    @Test
    void changePoints_abruptShift_detected() {
        var obs = List.of(
                Map.<String, FeatureValue>of("t", number(0), "hr", number(60)),
                Map.<String, FeatureValue>of("t", number(1), "hr", number(61)),
                Map.<String, FeatureValue>of("t", number(2), "hr", number(59)),
                Map.<String, FeatureValue>of("t", number(3), "hr", number(60)),
                Map.<String, FeatureValue>of("t", number(4), "hr", number(100)),
                Map.<String, FeatureValue>of("t", number(5), "hr", number(101)),
                Map.<String, FeatureValue>of("t", number(6), "hr", number(99)));
        var profile = TrendAnalyzer.analyze(obs, SCHEMA);
        assertThat(profile.metrics().get("vitals_change_points_hr")).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void changePoints_fewerThanThree_zero() {
        var obs = List.of(
                Map.<String, FeatureValue>of("t", number(0), "hr", number(60)),
                Map.<String, FeatureValue>of("t", number(1), "hr", number(100)));
        var profile = TrendAnalyzer.analyze(obs, SCHEMA);
        assertThat(profile.metrics().get("vitals_change_points_hr")).isEqualTo(0.0);
    }

    // -- DURATION --

    @Test
    void duration_multipleObservations() {
        var obs = List.of(
                Map.<String, FeatureValue>of("t", number(0), "hr", number(60)),
                Map.<String, FeatureValue>of("t", number(5), "hr", number(80)));
        var profile = TrendAnalyzer.analyze(obs, SCHEMA);
        assertThat(profile.metrics().get("vitals_duration")).isEqualTo(5.0);
    }

    @Test
    void duration_singleObservation_zero() {
        var obs = List.of(Map.<String, FeatureValue>of("t", number(0), "hr", number(60)));
        var profile = TrendAnalyzer.analyze(obs, SCHEMA);
        assertThat(profile.metrics().get("vitals_duration")).isEqualTo(0.0);
    }

    // -- OBSERVATION_COUNT --

    @Test
    void observationCount() {
        var obs = List.of(
                Map.<String, FeatureValue>of("t", number(0), "hr", number(60)),
                Map.<String, FeatureValue>of("t", number(1), "hr", number(70)),
                Map.<String, FeatureValue>of("t", number(2), "hr", number(80)));
        var profile = TrendAnalyzer.analyze(obs, SCHEMA);
        assertThat(profile.metrics().get("vitals_observation_count")).isEqualTo(3.0);
    }

    // -- Timestamp field excluded --

    @Test
    void timestampField_excludedFromPerFieldMetrics() {
        var obs = List.of(
                Map.<String, FeatureValue>of("t", number(0), "hr", number(60)),
                Map.<String, FeatureValue>of("t", number(1), "hr", number(70)));
        var profile = TrendAnalyzer.analyze(obs, SCHEMA);
        assertThat(profile.metrics()).doesNotContainKey("vitals_slope_t");
        assertThat(profile.metrics()).doesNotContainKey("vitals_delta_t");
        assertThat(profile.metrics()).containsKey("vitals_slope_hr");
    }

    // -- enrichFeatures --

    @Test
    void enrichFeatures_returnsNewMapWithDerivedFields() {
        var obs = List.of(
                Map.<String, FeatureValue>of("t", number(0), "hr", number(60)),
                Map.<String, FeatureValue>of("t", number(1), "hr", number(80)));
        var features = Map.<String, FeatureValue>of(
                "drug", string("aspirin"),
                "vitals", FeatureValue.structList(obs));
        var schema = CbrFeatureSchema.of("test",
                FeatureField.categorical("drug"),
                SCHEMA);
        var enriched = TrendAnalyzer.enrichFeatures(features, schema);
        assertThat(enriched).containsKey("drug");
        assertThat(enriched).containsKey("vitals");
        assertThat(enriched).containsKey("vitals_slope_hr");
        assertThat(enriched).containsKey("vitals_delta_hr");
        assertThat(features).doesNotContainKey("vitals_slope_hr");
    }

    @Test
    void enrichFeatures_noTimeSeries_returnsInputUnchanged() {
        var features = Map.<String, FeatureValue>of("drug", string("aspirin"));
        var schema = CbrFeatureSchema.of("test", FeatureField.categorical("drug"));
        var enriched = TrendAnalyzer.enrichFeatures(features, schema);
        assertThat(enriched).isSameAs(features);
    }

    // -- expandSchema --

    @Test
    void expandSchema_addsDerivedNumericFields() {
        var schema = CbrFeatureSchema.of("test",
                FeatureField.categorical("drug"),
                SCHEMA);
        var expanded = TrendAnalyzer.expandSchema(schema);
        var fieldNames = expanded.fields().stream().map(FeatureField::name).toList();
        assertThat(fieldNames).contains("vitals_slope_hr", "vitals_delta_hr",
                "vitals_volatility_hr", "vitals_acceleration_hr",
                "vitals_change_points_hr", "vitals_duration", "vitals_observation_count");
    }

    @Test
    void expandSchema_preservesOriginalFields() {
        var schema = CbrFeatureSchema.of("test",
                FeatureField.categorical("drug"),
                SCHEMA);
        var expanded = TrendAnalyzer.expandSchema(schema);
        var fieldNames = expanded.fields().stream().map(FeatureField::name).toList();
        assertThat(fieldNames).contains("drug", "vitals");
    }

    @Test
    void expandSchema_idempotent() {
        var schema = CbrFeatureSchema.of("test",
                FeatureField.categorical("drug"),
                SCHEMA);
        var expanded1 = TrendAnalyzer.expandSchema(schema);
        var expanded2 = TrendAnalyzer.expandSchema(expanded1);
        assertThat(expanded2.fields()).hasSize(expanded1.fields().size());
    }

    @Test
    void expandSchema_noTrendSpec_returnsIdentical() {
        var noTrendTs = (FeatureField) FeatureField.timeSeries("vitals", "t",
                FeatureField.numeric("t", 0, 100),
                FeatureField.numeric("hr", 40, 200));
        var schema = CbrFeatureSchema.of("test", noTrendTs);
        var expanded = TrendAnalyzer.expandSchema(schema);
        assertThat(expanded.fields()).hasSize(schema.fields().size());
    }

    @Test
    void expandSchema_derivedFieldRanges_heuristic() {
        var schema = CbrFeatureSchema.of("test", SCHEMA);
        var expanded = TrendAnalyzer.expandSchema(schema);
        var slopeField = expanded.fields().stream()
                .filter(f -> f.name().equals("vitals_slope_hr"))
                .findFirst().orElseThrow();
        assertThat(slopeField).isInstanceOf(FeatureField.Numeric.class);
        var numeric = (FeatureField.Numeric) slopeField;
        assertThat(numeric.min()).isEqualTo(-160.0);
        assertThat(numeric.max()).isEqualTo(160.0);
    }

    // -- Multiple inner fields --

    @Test
    void multipleInnerFields_eachGetsTrends() {
        var multiSchema = (FeatureField.TimeSeries) FeatureField.timeSeries("obs", "t",
                null,
                new TrendSpec(Set.of(TrendType.SLOPE), ChronoUnit.HOURS),
                FeatureField.numeric("t", 0, 100),
                FeatureField.numeric("a", 0, 50),
                FeatureField.numeric("b", 0, 100));
        var obs = List.of(
                Map.<String, FeatureValue>of("t", number(0), "a", number(10), "b", number(20)),
                Map.<String, FeatureValue>of("t", number(1), "a", number(20), "b", number(40)));
        var profile = TrendAnalyzer.analyze(obs, multiSchema);
        assertThat(profile.metrics().get("obs_slope_a")).isCloseTo(10.0, within(0.001));
        assertThat(profile.metrics().get("obs_slope_b")).isCloseTo(20.0, within(0.001));
        assertThat(profile.metrics()).doesNotContainKey("obs_slope_t");
    }

    // -- TrendProfile.toFeatures --

    @Test
    void trendProfile_toFeatures() {
        var profile = new TrendProfile(Map.of("vitals_slope_hr", 10.0, "vitals_delta_hr", 30.0));
        var features = profile.toFeatures();
        assertThat(features.get("vitals_slope_hr")).isEqualTo(FeatureValue.number(10.0));
        assertThat(features.get("vitals_delta_hr")).isEqualTo(FeatureValue.number(30.0));
    }

    // -- withFeatures --

    @Test
    void featureVectorCbrCase_withFeatures() {
        var original = new FeatureVectorCbrCase("p", "s", null, null,
                Map.of("a", string("x")));
        var updated = original.withFeatures(Map.of("a", string("x"), "b", number(1)));
        assertThat(updated.features()).containsKey("b");
        assertThat(updated.problem()).isEqualTo("p");
    }

    @Test
    void planCbrCase_withFeatures_preservesPlanTrace() {
        var trace = List.of(new PlanTrace("step1", "cap1", "worker1", "OK", 1, Map.of()));
        var original = new PlanCbrCase("p", "s", null, null, Map.of("a", string("x")), trace);
        var updated = (PlanCbrCase) original.withFeatures(Map.of("a", string("x"), "b", number(1)));
        assertThat(updated.features()).containsKey("b");
        assertThat(updated.planTrace()).hasSize(1);
    }

    @Test
    void textualCbrCase_withFeatures_throws() {
        var tc = new TextualCbrCase("p", "s", null, null);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> tc.withFeatures(Map.of("a", string("x"))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void cbrQuery_withFeatures() {
        var query = CbrQuery.of("t", CBR, "ct",
                Map.of("a", string("x")), 10);
        var updated = query.withFeatures(Map.of("a", string("x"), "b", number(1)));
        assertThat(updated.features()).containsKey("b");
        assertThat(updated.tenantId()).isEqualTo("t");
        assertThat(updated.topK()).isEqualTo(10);
    }
}
