package io.casehub.neocortex.memory.cbr;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CbrMatchTest {

    private static final String TYPE = "test-type";

    @Test
    void constructor_validScoreRange_succeeds() {
        var cbrRecord = new CbrGuidanceRecord("problem", "solution", null, null, null, null);
        assertThat(new CbrMatch<>(cbrRecord, TYPE, 1.0).score()).isEqualTo(1.0);
        assertThat(new CbrMatch<>(cbrRecord, TYPE, 0.0).score()).isEqualTo(0.0);
        assertThat(new CbrMatch<>(cbrRecord, TYPE, -1.0).score()).isEqualTo(-1.0);
    }

    @Test
    void constructor_scoreAboveOne_throws() {
        var cbrRecord = new CbrGuidanceRecord("problem", "solution", null, null, null, null);
        assertThatThrownBy(() -> new CbrMatch<>(cbrRecord, TYPE, 1.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score must be in [-1,1]");
    }

    @Test
    void constructor_scoreBelowMinusOne_throws() {
        var cbrRecord = new CbrGuidanceRecord("problem", "solution", null, null, null, null);
        assertThatThrownBy(() -> new CbrMatch<>(cbrRecord, TYPE, -1.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score must be in [-1,1]");
    }

    @Test
    void constructor_scoreNaN_throws() {
        var cbrRecord = new CbrGuidanceRecord("problem", "solution", null, null, null, null);
        assertThatThrownBy(() -> new CbrMatch<>(cbrRecord, TYPE, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score must be in [-1,1]");
    }

    @Test
    void constructor_scorePositiveInfinity_throws() {
        var cbrRecord = new CbrGuidanceRecord("problem", "solution", null, null, null, null);
        assertThatThrownBy(() -> new CbrMatch<>(cbrRecord, TYPE, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score must be in [-1,1]");
    }

    @Test
    void constructor_scoreNegativeInfinity_throws() {
        var cbrRecord = new CbrGuidanceRecord("problem", "solution", null, null, null, null);
        assertThatThrownBy(() -> new CbrMatch<>(cbrRecord, TYPE, Double.NEGATIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("score must be in [-1,1]");
    }

    @Test
    void constructor_nullCase_throws() {
        assertThatThrownBy(() -> new CbrMatch<>(null, TYPE, 0.5))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cbrRecord required");
    }

    @Test
    void constructor_twoArg_defaultsRerankedFalse() {
        var cbrRecord = new CbrGuidanceRecord("problem", "solution", null, null, null, null);
        assertThat(new CbrMatch<>(cbrRecord, TYPE, 0.5).reranked()).isFalse();
    }

    @Test
    void constructor_threeArg_setsReranked() {
        var cbrRecord = new CbrGuidanceRecord("problem", "solution", null, null, null, null);
        assertThat(new CbrMatch<>(cbrRecord, TYPE, 0.5, true).reranked()).isTrue();
    }

    @Test
    void withReranked_returnsNewInstanceWithRerankedTrue() {
        var cbrRecord  = new CbrGuidanceRecord("problem", "solution", null, null, null, null);
        var original = new CbrMatch<>(cbrRecord, TYPE, 0.8);
        var reranked = original.withReranked();
        assertThat(reranked.reranked()).isTrue();
        assertThat(reranked.score()).isEqualTo(0.8);
        assertThat(reranked.cbrRecord()).isSameAs(cbrRecord);
        assertThat(original.reranked()).isFalse();
    }

    @Test
    void featureSimilarities_present() {
        var cbrRecord = new CbrGuidanceRecord("problem", "solution", null, null, null, null);
        var sims    = java.util.Map.of("posture", 0.6, "size", 0.3);
        var scored  = new CbrMatch<>(cbrRecord, TYPE, 0.9, false, sims);
        assertThat(scored.featureSimilarities()).isEqualTo(sims);
    }

    @Test
    void featureSimilarities_immutable() {
        var cbrRecord = new CbrGuidanceRecord("problem", "solution", null, null, null, null);
        var sims    = new java.util.HashMap<String, Double>();
        sims.put("a", 0.5);
        var scored = new CbrMatch<>(cbrRecord, TYPE, 0.9, false, sims);
        assertThatThrownBy(() -> scored.featureSimilarities().put("b", 0.1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void twoArgConstructor_emptyFeatureSimilarities() {
        var cbrRecord = new CbrGuidanceRecord("problem", "solution", null, null, null, null);
        var scored  = new CbrMatch<>(cbrRecord, TYPE, 0.9);
        assertThat(scored.featureSimilarities()).isEmpty();
    }

    @Test
    void threeArgConstructor_emptyFeatureSimilarities() {
        var cbrRecord = new CbrGuidanceRecord("problem", "solution", null, null, null, null);
        var scored  = new CbrMatch<>(cbrRecord, TYPE, 0.9, true);
        assertThat(scored.featureSimilarities()).isEmpty();
    }

    @Test
    void withReranked_preservesFeatureSimilarities() {
        var cbrRecord  = new CbrGuidanceRecord("problem", "solution", null, null, null, null);
        var sims     = java.util.Map.of("posture", 0.6);
        var scored   = new CbrMatch<>(cbrRecord, TYPE, 0.9, false, sims);
        var reranked = scored.withReranked();
        assertThat(reranked.reranked()).isTrue();
        assertThat(reranked.featureSimilarities()).isEqualTo(sims);
    }

    @Test
    void nullFeatureSimilarities_becomesEmpty() {
        var cbrRecord = new CbrGuidanceRecord("problem", "solution", null, null, null, null);
        var scored  = new CbrMatch<>(cbrRecord, TYPE, 0.9, false, null);
        assertThat(scored.featureSimilarities()).isEmpty();
    }

    @Test
    void caseId_present() {
        var cbrRecord = new CbrGuidanceRecord("problem", "solution", null, null, null, null);
        var scored  = new CbrMatch<>(cbrRecord, "case-1", TYPE, 0.9);
        assertThat(scored.caseId()).isEqualTo("case-1");
    }

    @Test
    void caseId_null_allowed() {
        var cbrRecord = new CbrGuidanceRecord("problem", "solution", null, null, null, null);
        var scored  = new CbrMatch<>(cbrRecord, TYPE, 0.9);
        assertThat(scored.caseId()).isNull();
    }

    @Test
    void withReranked_preservesCaseId() {
        var cbrRecord  = new CbrGuidanceRecord("problem", "solution", null, null, null, null);
        var original = new CbrMatch<>(cbrRecord, "case-1", TYPE, 0.8);
        var reranked = original.withReranked();
        assertThat(reranked.caseId()).isEqualTo("case-1");
        assertThat(reranked.reranked()).isTrue();
    }


    @Test
    void storedAt_includedInCanonicalConstructor() {
        var now    = java.time.Instant.now();
        var scored = new CbrMatch<>(textCase(), "c1", TYPE, 0.9, false, java.util.Map.of(), now, io.casehub.platform.api.path.Path.root(), null);
        assertThat(scored.storedAt()).isEqualTo(now);
    }

    @Test
    void storedAt_nullableAndDefaultsToNull() {
        var scored = new CbrMatch<>(textCase(), "c1", TYPE, 0.9, false, java.util.Map.of(), null, io.casehub.platform.api.path.Path.root(), null);
        assertThat(scored.storedAt()).isNull();
    }

    @Test
    void convenienceConstructors_defaultStoredAtToNull() {
        assertThat(new CbrMatch<>(textCase(), "c1", TYPE, 0.9).storedAt()).isNull();
        assertThat(new CbrMatch<>(textCase(), TYPE, 0.9).storedAt()).isNull();
        assertThat(new CbrMatch<>(textCase(), TYPE, 0.9, false).storedAt()).isNull();
        assertThat(new CbrMatch<>(textCase(), TYPE, 0.9, false, java.util.Map.of()).storedAt()).isNull();
    }

    @Test
    void withScore_preservesAllFieldsExceptScore() {
        var now      = java.time.Instant.now();
        var original = new CbrMatch<>(textCase(), "c1", TYPE, 0.9, true, java.util.Map.of("f", 0.8), now, io.casehub.platform.api.path.Path.root(), null);
        var modified = original.withScore(0.5);
        assertThat(modified.score()).isEqualTo(0.5);
        assertThat(modified.cbrRecord()).isSameAs(original.cbrRecord());
        assertThat(modified.caseId()).isEqualTo("c1");
        assertThat(modified.caseType()).isEqualTo(TYPE);
        assertThat(modified.reranked()).isTrue();
        assertThat(modified.featureSimilarities()).isEqualTo(java.util.Map.of("f", 0.8));
        assertThat(modified.storedAt()).isEqualTo(now);
    }

    @Test
    void withReranked_preservesStoredAt() {
        var now      = java.time.Instant.now();
        var original = new CbrMatch<>(textCase(), "c1", TYPE, 0.9, false, java.util.Map.of("f", 0.8), now, io.casehub.platform.api.path.Path.root(), null);
        var reranked = original.withReranked();
        assertThat(reranked.reranked()).isTrue();
        assertThat(reranked.score()).isEqualTo(0.9);
        assertThat(reranked.caseType()).isEqualTo(TYPE);
        assertThat(reranked.storedAt()).isEqualTo(now);
        assertThat(reranked.featureSimilarities()).isEqualTo(java.util.Map.of("f", 0.8));
    }

    // --- caseType tests ---

    @Test
    void caseType_requiredOnCanonicalConstructor() {
        assertThatThrownBy(() -> new CbrMatch<>(textCase(), "c1", null, 0.9, false,
                                                java.util.Map.of(), null, io.casehub.platform.api.path.Path.root(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("caseType required");
    }

    @Test
    void caseType_presentOnCanonicalConstructor() {
        var scored = new CbrMatch<>(textCase(), "c1", "my-type", 0.9, false,
                                    java.util.Map.of(), null, io.casehub.platform.api.path.Path.root(), null);
        assertThat(scored.caseType()).isEqualTo("my-type");
    }

    @Test
    void withCaseType_returnsNewInstance() {
        var scored = new CbrMatch<>(textCase(), "c1", "type-a", 0.9, false,
                                    java.util.Map.of(), null, io.casehub.platform.api.path.Path.root(), null);
        var updated = scored.withCaseType("type-b");
        assertThat(updated.caseType()).isEqualTo("type-b");
        assertThat(updated.score()).isEqualTo(0.9);
        assertThat(updated.caseId()).isEqualTo("c1");
        assertThat(scored.caseType()).isEqualTo("type-a");
    }

    @Test
    void withCaseType_preservesAllOtherFields() {
        var now = java.time.Instant.now();
        var original = new CbrMatch<>(textCase(), "c1", "type-a", 0.9, true,
                                      java.util.Map.of("f", 0.8), now, io.casehub.platform.api.path.Path.root(), 0.5);
        var updated = original.withCaseType("type-b");
        assertThat(updated.cbrRecord()).isSameAs(original.cbrRecord());
        assertThat(updated.caseId()).isEqualTo("c1");
        assertThat(updated.score()).isEqualTo(0.9);
        assertThat(updated.reranked()).isTrue();
        assertThat(updated.featureSimilarities()).isEqualTo(java.util.Map.of("f", 0.8));
        assertThat(updated.storedAt()).isEqualTo(now);
        assertThat(updated.trustTrajectory()).isEqualTo(0.5);
    }

    private CbrGuidanceRecord textCase() {
        return new CbrGuidanceRecord("problem", "solution", null, null, null, null);
    }
}
