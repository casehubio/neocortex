package io.casehub.neocortex.rag;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FederationQueryTest {

    @Test
    void visitedSet_defensivelyCopied() {
        var visited = new HashSet<>(Set.of("a", "b"));
        var query = new FederationQuery("local", "text", 10, visited, Map.of());
        visited.add("c");
        assertThat(query.visited()).doesNotContain("c");
    }

    @Test
    void visitedSet_immutable() {
        var query = new FederationQuery("local", "text", 10, Set.of("a"), Map.of());
        assertThatThrownBy(() -> query.visited().add("b"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void filterContext_nullBecomesEmpty() {
        var query = new FederationQuery("local", "text", 10, Set.of(), null);
        assertThat(query.filterContext()).isEmpty();
    }

    @Test
    void nullLocalId_rejected() {
        assertThatThrownBy(() -> new FederationQuery(null, "text", 10, Set.of(), Map.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullQueryText_rejected() {
        assertThatThrownBy(() -> new FederationQuery("local", null, 10, Set.of(), Map.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void federatedResult_metadataNullBecomesEmpty() {
        var result = new FederatedResult("content", 0.9, "src1", null);
        assertThat(result.metadata()).isEmpty();
    }

    @Test
    void federatedResult_metadataImmutable() {
        var result = new FederatedResult("content", 0.9, "src1", Map.of("k", "v"));
        assertThatThrownBy(() -> result.metadata().put("k2", "v2"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void federationTarget_allFieldsRequired() {
        assertThatThrownBy(() -> new FederationTarget(null, "id", FederationTarget.Relationship.UPSTREAM))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FederationTarget("url", null, FederationTarget.Relationship.UPSTREAM))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FederationTarget("url", "id", null))
            .isInstanceOf(NullPointerException.class);
    }
}
