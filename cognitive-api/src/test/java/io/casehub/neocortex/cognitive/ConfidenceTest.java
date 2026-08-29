package io.casehub.neocortex.cognitive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ConfidenceTest {

    @Test
    void constructorStoresFields() {
        var now = Instant.now();
        var c = new Confidence(ConfidenceOrigin.STATED, 0.8, now);
        assertThat(c.origin()).isEqualTo(ConfidenceOrigin.STATED);
        assertThat(c.value()).isEqualTo(0.8);
        assertThat(c.decayReference()).isEqualTo(now);
    }

    @Test
    void nullOriginThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Confidence(null, 0.5, null));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.001, 1.001, -1.0, 2.0})
    void outOfRangeThrows(double v) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Confidence(ConfidenceOrigin.STATED, v, null));
    }

    @Test
    void nanThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Confidence(ConfidenceOrigin.STATED, Double.NaN, null));
    }

    @Test
    void boundaryValuesAccepted() {
        assertThatNoException().isThrownBy(() -> new Confidence(ConfidenceOrigin.STATED, 0.0, null));
        assertThatNoException().isThrownBy(() -> new Confidence(ConfidenceOrigin.STATED, 1.0, null));
    }

    @Test
    void infinityThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Confidence(ConfidenceOrigin.STATED, Double.POSITIVE_INFINITY, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Confidence(ConfidenceOrigin.STATED, Double.NEGATIVE_INFINITY, null));
    }

    @Test
    void statedFactoryEnforcesDecayReference() {
        var now = Instant.now();
        var c = Confidence.stated(1.0, now);
        assertThat(c.origin()).isEqualTo(ConfidenceOrigin.STATED);
        assertThat(c.value()).isEqualTo(1.0);
        assertThat(c.decayReference()).isEqualTo(now);
    }

    @Test
    void statedFactoryNullDecayReferenceThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> Confidence.stated(1.0, null));
    }

    @Test
    void inferredFactoryEnforcesDecayReference() {
        var now = Instant.now();
        var c = Confidence.inferred(0.7, now);
        assertThat(c.origin()).isEqualTo(ConfidenceOrigin.INFERRED);
        assertThat(c.value()).isEqualTo(0.7);
        assertThat(c.decayReference()).isEqualTo(now);
    }

    @Test
    void inferredFactoryNullDecayReferenceThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> Confidence.inferred(0.7, null));
    }

    @Test
    void speculatedFactoryEnforcesDecayReference() {
        var now = Instant.now();
        var c = Confidence.speculated(0.3, now);
        assertThat(c.origin()).isEqualTo(ConfidenceOrigin.SPECULATED);
        assertThat(c.value()).isEqualTo(0.3);
        assertThat(c.decayReference()).isEqualTo(now);
    }

    @Test
    void speculatedFactoryNullDecayReferenceThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> Confidence.speculated(0.3, null));
    }

    @Test
    void unknownFactoryNullDecayReference() {
        var c = Confidence.unknown(0.5);
        assertThat(c.origin()).isEqualTo(ConfidenceOrigin.UNKNOWN);
        assertThat(c.value()).isEqualTo(0.5);
        assertThat(c.decayReference()).isNull();
    }

    @Test
    void withValuePreservesOtherFields() {
        var now = Instant.now();
        var c = Confidence.stated(1.0, now).withValue(0.5);
        assertThat(c.origin()).isEqualTo(ConfidenceOrigin.STATED);
        assertThat(c.value()).isEqualTo(0.5);
        assertThat(c.decayReference()).isEqualTo(now);
    }

    @Test
    void withValueValidatesRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Confidence.unknown(0.5).withValue(1.5));
    }

    @Test
    void withDecayReferencePreservesOtherFields() {
        var now = Instant.now();
        var later = now.plusSeconds(3600);
        var c = Confidence.stated(0.8, now).withDecayReference(later);
        assertThat(c.origin()).isEqualTo(ConfidenceOrigin.STATED);
        assertThat(c.value()).isEqualTo(0.8);
        assertThat(c.decayReference()).isEqualTo(later);
    }

    @Test
    void nullDecayReferenceAllowedOnConstructor() {
        var c = new Confidence(ConfidenceOrigin.UNKNOWN, 0.5, null);
        assertThat(c.decayReference()).isNull();
    }

    @Test
    void recordEquality() {
        var now = Instant.now();
        var a = Confidence.stated(0.8, now);
        var b = Confidence.stated(0.8, now);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void recordInequality() {
        var now = Instant.now();
        var a = Confidence.stated(0.8, now);
        var b = Confidence.inferred(0.8, now);
        assertThat(a).isNotEqualTo(b);
    }
}
