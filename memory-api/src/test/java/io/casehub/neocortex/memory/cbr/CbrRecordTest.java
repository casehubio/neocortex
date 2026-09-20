package io.casehub.neocortex.memory.cbr;

import io.casehub.neocortex.cognitive.Confidence;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.casehub.neocortex.memory.cbr.FeatureValue.number;
import static io.casehub.neocortex.memory.cbr.FeatureValue.string;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CbrRecordTest {

    @Test
    void guidanceRecord_valid() {
        var c = new CbrGuidanceRecord("problem", "solution", "WIN", Confidence.unknown(0.9), null, null);
        assertThat(c.problem()).isEqualTo("problem");
        assertThat(c.solution()).isEqualTo("solution");
        assertThat(c.outcome()).isEqualTo("WIN");
        assertThat(c.confidence().value()).isEqualTo(0.9);
    }

    @Test
    void guidanceRecord_nullOutcomeAllowed() {
        var c = new CbrGuidanceRecord("problem", "solution", null, null, null, null);
        assertThat(c.outcome()).isNull();
        assertThat(c.confidence()).isNull();
    }

    @Test
    void guidanceRecord_nullProblemRejected() {
        assertThatThrownBy(() -> new CbrGuidanceRecord(null, "solution", null, null, null, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void guidanceRecord_blankProblemRejected() {
        assertThatThrownBy(() -> new CbrGuidanceRecord("  ", "solution", null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void guidanceRecord_confidenceOutOfRange() {
        assertThatThrownBy(() -> new CbrGuidanceRecord("p", "s", null, Confidence.unknown(1.1), null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void guidanceRecord_implementsCbrRecord() {
        CbrRecord c = new CbrGuidanceRecord("p", "s", null, null, null, null);
        assertThat(c.problem()).isEqualTo("p");
    }

    @Test
    void featureRecord_valid() {
        var features = Map.<String, FeatureValue>of("race", string("Zerg"), "ratio", number(0.7));
        var c = new CbrFeatureRecord("problem", "solution", "WIN", Confidence.unknown(0.8), features, null, null);
        assertThat(c.features()).containsEntry("race", string("Zerg"));
    }

    @Test
    void featureRecord_featuresDefensivelyCopied() {
        var features = new java.util.HashMap<String, FeatureValue>();
        features.put("race", string("Zerg"));
        var c = new CbrFeatureRecord("p", "s", null, null, features, null, null);
        features.put("extra", string("value"));
        assertThat(c.features()).doesNotContainKey("extra");
    }

    @Test
    void featureRecord_nullFeaturesRejected() {
        assertThatThrownBy(() -> new CbrFeatureRecord("p", "s", null, null, null, null, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void guidanceRecord_recordType_returns_textual() {
        var c = new CbrGuidanceRecord("problem", "solution", null, null, null, null);
        assertThat(c.recordType()).isEqualTo("textual");
    }

    @Test
    void featureRecord_recordType_returns_feature_vector() {
        var c = new CbrFeatureRecord("p", "s", null, null, Map.of("race", string("Zerg")), null, null);
        assertThat(c.recordType()).isEqualTo("feature-vector");
    }

    @Test
    void recordType_constants_match_method_return() {
        assertThat(CbrGuidanceRecord.CBR_TYPE).isEqualTo("textual");
        assertThat(CbrFeatureRecord.CBR_TYPE).isEqualTo("feature-vector");
        assertThat(new CbrGuidanceRecord("p", "s", null, null, null, null).recordType()).isEqualTo(CbrGuidanceRecord.CBR_TYPE);
        assertThat(new CbrFeatureRecord("p", "s", null, null, Map.of(), null, null).recordType()).isEqualTo(CbrFeatureRecord.CBR_TYPE);
    }

    @Test
    void cbrMatch_rejectsScoreAboveOne() {
        var c = new CbrGuidanceRecord("p", "s", null, null, null, null);
        assertThatThrownBy(() -> new CbrMatch<>(c, "t", 1.1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("[-1,1]");
    }

    @Test
    void cbrMatch_rejectsScoreBelowNegativeOne() {
        var c = new CbrGuidanceRecord("p", "s", null, null, null, null);
        assertThatThrownBy(() -> new CbrMatch<>(c, "t", -1.1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("[-1,1]");
    }

    @Test
    void cbrMatch_rejectsNaN() {
        var c = new CbrGuidanceRecord("p", "s", null, null, null, null);
        assertThatThrownBy(() -> new CbrMatch<>(c, "t", Double.NaN))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cbrMatch_rejectsPositiveInfinity() {
        var c = new CbrGuidanceRecord("p", "s", null, null, null, null);
        assertThatThrownBy(() -> new CbrMatch<>(c, "t", Double.POSITIVE_INFINITY))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cbrMatch_acceptsBoundaryValues() {
        var c = new CbrGuidanceRecord("p", "s", null, null, null, null);
        assertThatCode(() -> new CbrMatch<>(c, "t", 1.0)).doesNotThrowAnyException();
        assertThatCode(() -> new CbrMatch<>(c, "t", -1.0)).doesNotThrowAnyException();
        assertThatCode(() -> new CbrMatch<>(c, "t", 0.0)).doesNotThrowAnyException();
        assertThatCode(() -> new CbrMatch<>(c, "t", 0.75)).doesNotThrowAnyException();
    }

    @Test
    void featureVectorCase_withOutcome_preservesFields() {
        var original = new CbrFeatureRecord("prob", "sol", null, Confidence.unknown(0.8),
                                            Map.of("race", string("Zerg")), null, null);
        CbrRecord updated = original.withOutcome("SUCCESS", Confidence.unknown(0.84));
        assertThat(updated.outcome()).isEqualTo("SUCCESS");
        assertThat(updated.confidence().value()).isEqualTo(0.84);
        assertThat(updated.problem()).isEqualTo("prob");
        assertThat(updated.solution()).isEqualTo("sol");
        assertThat(updated.features()).isEqualTo(original.features());
    }

    @Test
    void planCase_withOutcome_preservesPlanTrace() {
        var trace = new CbrPlanStep("bind", "cap", "worker", "SUCCESS", 1, Map.of(), null);
        var original = new CbrPlanRecord("prob", "sol", null, null,
                                         Map.of(), java.util.List.of(trace), null, null);
        CbrRecord updated = original.withOutcome("FAILURE", Confidence.unknown(0.64));
        assertThat(updated.outcome()).isEqualTo("FAILURE");
        assertThat(updated.confidence().value()).isEqualTo(0.64);
        assertThat(updated).isInstanceOf(CbrPlanRecord.class);
        assertThat(((CbrPlanRecord) updated).cbrPlanStep()).containsExactly(trace);
    }

    @Test
    void textualCase_withOutcome() {
        var       original = new CbrGuidanceRecord("prob", "sol", null, null, null, null);
        CbrRecord updated  = original.withOutcome("PARTIAL", Confidence.unknown(0.74));
        assertThat(updated.outcome()).isEqualTo("PARTIAL");
        assertThat(updated.confidence().value()).isEqualTo(0.74);
        assertThat(updated.problem()).isEqualTo("prob");
    }
}
