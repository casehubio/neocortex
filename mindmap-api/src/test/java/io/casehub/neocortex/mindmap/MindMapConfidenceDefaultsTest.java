package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MindMapConfidenceDefaultsTest {

    @Test
    void statedDefaultsTo1() {
        assertThat(MindMapConfidenceDefaults.defaultValue(ConfidenceOrigin.STATED)).isEqualTo(1.0);
    }

    @Test
    void inferredDefaultsTo07() {
        assertThat(MindMapConfidenceDefaults.defaultValue(ConfidenceOrigin.INFERRED)).isEqualTo(0.7);
    }

    @Test
    void speculatedDefaultsTo03() {
        assertThat(MindMapConfidenceDefaults.defaultValue(ConfidenceOrigin.SPECULATED)).isEqualTo(0.3);
    }

    @Test
    void unknownDefaultsTo1() {
        assertThat(MindMapConfidenceDefaults.defaultValue(ConfidenceOrigin.UNKNOWN)).isEqualTo(1.0);
    }

    @Test
    void forOriginStated_enforcesDecayReference() {
        var now = Instant.now();
        var c = MindMapConfidenceDefaults.forOrigin(ConfidenceOrigin.STATED, now);
        assertThat(c.origin()).isEqualTo(ConfidenceOrigin.STATED);
        assertThat(c.value()).isEqualTo(1.0);
        assertThat(c.decayReference()).isEqualTo(now);
    }

    @Test
    void forOriginStated_nullDecayReferenceThrows() {
        assertThatNullPointerException()
            .isThrownBy(() -> MindMapConfidenceDefaults.forOrigin(ConfidenceOrigin.STATED, null));
    }

    @Test
    void forOriginUnknown_nullDecayReferenceAccepted() {
        var c = MindMapConfidenceDefaults.forOrigin(ConfidenceOrigin.UNKNOWN, null);
        assertThat(c.origin()).isEqualTo(ConfidenceOrigin.UNKNOWN);
        assertThat(c.decayReference()).isNull();
    }
}
