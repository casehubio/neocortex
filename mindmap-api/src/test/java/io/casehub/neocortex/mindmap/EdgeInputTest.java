package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class EdgeInputTest {

    @Test
    void of_createsWithDefaults() {
        var e = EdgeInput.of("src", "tgt", "KNOWS");
        assertThat(e.sourceNodeId()).isEqualTo("src");
        assertThat(e.targetNodeId()).isEqualTo("tgt");
        assertThat(e.edgeType()).isEqualTo("KNOWS");
        assertThat(e.confidence()).isNull();
        assertThat(e.provenance()).isNull();
        assertThat(e.validFrom()).isNull();
        assertThat(e.validUntil()).isNull();
        assertThat(e.pleasure()).isNull();
        assertThat(e.arousal()).isNull();
        assertThat(e.dominance()).isNull();
        assertThat(e.properties()).isEmpty();
    }

    @Test
    void of_nullSourceNodeId_throws() {
        assertThatThrownBy(() -> EdgeInput.of(null, "tgt", "KNOWS"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void withConfidence_returnsNewInstance() {
        var e = EdgeInput.of("src", "tgt", "KNOWS");
        var conf = new Confidence(ConfidenceOrigin.STATED, 0.8, null);
        var e2 = e.withConfidence(conf);
        assertThat(e2.confidence()).isEqualTo(conf);
        assertThat(e.confidence()).isNull();
    }

    @Test
    void chaining_setsMultipleFields() {
        var now = Instant.now();
        var e = EdgeInput.of("src", "tgt", "KNOWS")
            .withProvenance("test")
            .withPad(0.5, 0.3, 0.7)
            .withValidFrom(now);
        assertThat(e.provenance()).isEqualTo("test");
        assertThat(e.pleasure()).isEqualTo(0.5);
        assertThat(e.validFrom()).isEqualTo(now);
    }

    @Test
    void withProperty_mergesIntoExisting() {
        var e = EdgeInput.of("src", "tgt", "KNOWS")
            .withProperties(Map.of("k1", "v1"))
            .withProperty("k2", "v2");
        assertThat(e.properties()).containsEntry("k1", "v1")
                                  .containsEntry("k2", "v2");
    }

    @Test
    void withProperties_defensivelyCopies() {
        var mutable = new HashMap<String, String>();
        mutable.put("k", "v");
        var e = EdgeInput.of("src", "tgt", "KNOWS").withProperties(mutable);
        mutable.put("k2", "v2");
        assertThat(e.properties()).hasSize(1);
    }
}
