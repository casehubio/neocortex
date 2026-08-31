package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class NodeInputTest {

    @Test
    void of_createsWithDefaults() {
        var n = NodeInput.of("Alice", "sg1");
        assertThat(n.name()).isEqualTo("Alice");
        assertThat(n.subgraphId()).isEqualTo("sg1");
        assertThat(n.confidence()).isNull();
        assertThat(n.provenance()).isNull();
        assertThat(n.traits()).isEmpty();
        assertThat(n.refs()).isEmpty();
        assertThat(n.validFrom()).isNull();
        assertThat(n.validUntil()).isNull();
        assertThat(n.pleasure()).isNull();
        assertThat(n.arousal()).isNull();
        assertThat(n.dominance()).isNull();
        assertThat(n.properties()).isEmpty();
    }

    @Test
    void of_blankName_throws() {
        assertThatThrownBy(() -> NodeInput.of("", "sg1"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_blankSubgraphId_throws() {
        assertThatThrownBy(() -> NodeInput.of("Alice", ""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withConfidence_returnsNewInstance() {
        var n = NodeInput.of("Alice", "sg1");
        var conf = new Confidence(ConfidenceOrigin.STATED, 0.9, null);
        var n2 = n.withConfidence(conf);
        assertThat(n2.confidence()).isEqualTo(conf);
        assertThat(n.confidence()).isNull();
    }

    @Test
    void chaining_setsMultipleFields() {
        var now = Instant.now();
        var n = NodeInput.of("Alice", "sg1")
            .withTraits(Set.of("person"))
            .withProvenance("test")
            .withPad(0.8, 0.3, 0.5)
            .withValidFrom(now);
        assertThat(n.traits()).containsExactly("person");
        assertThat(n.provenance()).isEqualTo("test");
        assertThat(n.pleasure()).isEqualTo(0.8);
        assertThat(n.arousal()).isEqualTo(0.3);
        assertThat(n.dominance()).isEqualTo(0.5);
        assertThat(n.validFrom()).isEqualTo(now);
    }

    @Test
    void withProperty_mergesIntoExisting() {
        var n = NodeInput.of("Alice", "sg1")
            .withProperties(Map.of("k1", "v1"))
            .withProperty("k2", "v2");
        assertThat(n.properties()).containsEntry("k1", "v1")
                                  .containsEntry("k2", "v2");
    }

    @Test
    void withProperties_defensivelyCopies() {
        var mutable = new HashMap<String, String>();
        mutable.put("k", "v");
        var n = NodeInput.of("Alice", "sg1").withProperties(mutable);
        mutable.put("k2", "v2");
        assertThat(n.properties()).hasSize(1);
    }

    @Test
    void withRefs_setsField() {
        var ref = new NodeRef("github", "123", null);
        var n = NodeInput.of("Alice", "sg1").withRefs(Set.of(ref));
        assertThat(n.refs()).containsExactly(ref);
    }

    @Test
    void withValidUntil_setsField() {
        var future = Instant.now().plusSeconds(3600);
        var n = NodeInput.of("Alice", "sg1").withValidUntil(future);
        assertThat(n.validUntil()).isEqualTo(future);
    }
}
