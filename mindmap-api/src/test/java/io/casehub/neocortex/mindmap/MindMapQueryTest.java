package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class MindMapQueryTest {

    @Test
    void of_createsQueryWithDefaults() {
        var q = MindMapQuery.of("t1", 50);
        assertThat(q.tenantId()).isEqualTo("t1");
        assertThat(q.limit()).isEqualTo(50);
        assertThat(q.subgraphId()).isNull();
        assertThat(q.text()).isNull();
        assertThat(q.edgeType()).isNull();
        assertThat(q.traits()).isNull();
        assertThat(q.minConfidence()).isNull();
        assertThat(q.confidenceOrigin()).isNull();
        assertThat(q.includeSuperseded()).isFalse();
        assertThat(q.validAfter()).isNull();
        assertThat(q.validBefore()).isNull();
        assertThat(q.updatedAfter()).isNull();
    }

    @Test
    void of_nullTenantId_throws() {
        assertThatThrownBy(() -> MindMapQuery.of(null, 50))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void of_zeroLimit_throws() {
        assertThatThrownBy(() -> MindMapQuery.of("t1", 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withText_returnsNewInstance() {
        var q = MindMapQuery.of("t1", 10);
        var q2 = q.withText("Alice");
        assertThat(q2.text()).isEqualTo("Alice");
        assertThat(q2.tenantId()).isEqualTo("t1");
        assertThat(q.text()).isNull();
    }

    @Test
    void chaining_setsMultipleFields() {
        var now = Instant.now();
        var q = MindMapQuery.of("t1", 20)
            .withText("search")
            .withSubgraphId("sg1")
            .withMinConfidence(0.5)
            .withValidAfter(now)
            .withIncludeSuperseded(true);
        assertThat(q.text()).isEqualTo("search");
        assertThat(q.subgraphId()).isEqualTo("sg1");
        assertThat(q.minConfidence()).isEqualTo(0.5);
        assertThat(q.validAfter()).isEqualTo(now);
        assertThat(q.includeSuperseded()).isTrue();
    }

    @Test
    void withTraits_defensivelyCopies() {
        var mutable = new HashSet<>(Set.of("person"));
        var q = MindMapQuery.of("t1", 10).withTraits(mutable);
        mutable.add("org");
        assertThat(q.traits()).containsExactly("person");
    }

    @Test
    void withConfidenceOrigin_setsField() {
        var q = MindMapQuery.of("t1", 10)
            .withConfidenceOrigin(ConfidenceOrigin.STATED);
        assertThat(q.confidenceOrigin()).isEqualTo(ConfidenceOrigin.STATED);
    }

    @Test
    void withEdgeType_setsField() {
        var q = MindMapQuery.of("t1", 10).withEdgeType("KNOWS");
        assertThat(q.edgeType()).isEqualTo("KNOWS");
    }

    @Test
    void withValidBefore_setsField() {
        var now = Instant.now();
        var q = MindMapQuery.of("t1", 10).withValidBefore(now);
        assertThat(q.validBefore()).isEqualTo(now);
    }

    @Test
    void withUpdatedAfter_setsField() {
        var now = Instant.now();
        var q = MindMapQuery.of("t1", 10).withUpdatedAfter(now);
        assertThat(q.updatedAfter()).isEqualTo(now);
    }
}
