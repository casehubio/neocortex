package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.MemoryDomain;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CognitiveProfileQueryTest {

    private static final String TENANT = "test-tenant";
    private static final String NODE_ID = "node-123";
    private static final String NAME = "Alice";
    private static final String SUBGRAPH = "family";

    @Test
    void byId_createsQueryWithDefaults() {
        var query = CognitiveProfileQuery.byId(NODE_ID, TENANT);

        assertThat(query.nodeId()).isEqualTo(NODE_ID);
        assertThat(query.entityName()).isNull();
        assertThat(query.subgraphId()).isNull();
        assertThat(query.tenantId()).isEqualTo(TENANT);
        assertThat(query.domains()).isEmpty();
        assertThat(query.includeEdges()).isTrue();
        assertThat(query.memoryLimit()).isEqualTo(50);
    }

    @Test
    void byName_createsQueryWithDefaults() {
        var query = CognitiveProfileQuery.byName(NAME, TENANT);

        assertThat(query.nodeId()).isNull();
        assertThat(query.entityName()).isEqualTo(NAME);
        assertThat(query.subgraphId()).isNull();
        assertThat(query.tenantId()).isEqualTo(TENANT);
    }

    @Test
    void byName_withSubgraph() {
        var query = CognitiveProfileQuery.byName(NAME, SUBGRAPH, TENANT);

        assertThat(query.entityName()).isEqualTo(NAME);
        assertThat(query.subgraphId()).isEqualTo(SUBGRAPH);
    }

    @Test
    void withDomains_returnsNewInstance() {
        var original = CognitiveProfileQuery.byId(NODE_ID, TENANT);
        var modified = original.withDomains(Set.of(new MemoryDomain("experience")));

        assertThat(modified.domains()).hasSize(1);
        assertThat(original.domains()).isEmpty();
    }

    @Test
    void withIncludeEdges_returnsNewInstance() {
        var query = CognitiveProfileQuery.byId(NODE_ID, TENANT).withIncludeEdges(false);
        assertThat(query.includeEdges()).isFalse();
    }

    @Test
    void withMemoryLimit_returnsNewInstance() {
        var query = CognitiveProfileQuery.byId(NODE_ID, TENANT).withMemoryLimit(10);
        assertThat(query.memoryLimit()).isEqualTo(10);
    }

    @Test
    void rejectsNullTenantId() {
        assertThatThrownBy(() -> CognitiveProfileQuery.byId(NODE_ID, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBothNodeIdAndEntityName() {
        assertThatThrownBy(() -> new CognitiveProfileQuery(
            NODE_ID, NAME, null, TENANT, Set.of(), true, 50))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNeitherNodeIdNorEntityName() {
        assertThatThrownBy(() -> new CognitiveProfileQuery(
            null, null, null, TENANT, Set.of(), true, 50))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidMemoryLimit() {
        assertThatThrownBy(() -> CognitiveProfileQuery.byId(NODE_ID, TENANT).withMemoryLimit(0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
