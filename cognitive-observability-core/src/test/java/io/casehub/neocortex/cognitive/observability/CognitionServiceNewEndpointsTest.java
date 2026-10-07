package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CognitionServiceNewEndpointsTest {

    private InMemoryMindMapStore mindMapStore;
    private InMemoryMemoryStore memoryStore;
    private CognitionService service;
    private static final String TENANT = "t1";

    private final CurrentPrincipal principal = new CurrentPrincipal() {
        @Override public String actorId() { return "actor"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return true; }
    };

    @BeforeEach
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        memoryStore = new InMemoryMemoryStore(principal);
        service = new CognitionService(mindMapStore, null, null, memoryStore, null, null);
    }

    @Test
    void graphTraversal_returnsConnectedNodes() {
        String sg = mindMapStore.createSubgraph(new SubgraphInput("test", SubgraphTypes.GENERAL, null), TENANT);
        String n1 = mindMapStore.addNode(NodeInput.of("Alice", sg), TENANT);
        String n2 = mindMapStore.addNode(NodeInput.of("Bob", sg), TENANT);
        String n3 = mindMapStore.addNode(NodeInput.of("Carol", sg), TENANT);
        mindMapStore.addEdge(EdgeInput.of(n1, n2, "knows"), TENANT);
        mindMapStore.addEdge(EdgeInput.of(n2, n3, "knows"), TENANT);

        GraphTraversalResult result = service.graph(TENANT, n1, 2, null, null);

        assertThat(result.focusNodeId()).isEqualTo(n1);
        assertThat(result.totalNodes()).isEqualTo(3);
        assertThat(result.nodesByDepth().get(0)).hasSize(1);
        assertThat(result.nodesByDepth().get(1)).hasSize(1);
        assertThat(result.nodesByDepth().get(2)).hasSize(1);
    }

    @Test
    void graphTraversal_respectsMaxDepth() {
        String sg = mindMapStore.createSubgraph(new SubgraphInput("test", SubgraphTypes.GENERAL, null), TENANT);
        String n1 = mindMapStore.addNode(NodeInput.of("Alice", sg), TENANT);
        String n2 = mindMapStore.addNode(NodeInput.of("Bob", sg), TENANT);
        String n3 = mindMapStore.addNode(NodeInput.of("Carol", sg), TENANT);
        mindMapStore.addEdge(EdgeInput.of(n1, n2, "knows"), TENANT);
        mindMapStore.addEdge(EdgeInput.of(n2, n3, "knows"), TENANT);

        GraphTraversalResult result = service.graph(TENANT, n1, 1, null, null);

        assertThat(result.totalNodes()).isEqualTo(2);
        assertThat(result.nodesByDepth()).doesNotContainKey(2);
    }

    @Test
    void graphTraversal_unknownNode_returnsEmpty() {
        GraphTraversalResult result = service.graph(TENANT, "nonexistent", 2, null, null);

        assertThat(result.totalNodes()).isEqualTo(0);
    }

    @Test
    void analytics_returnsAggregatedMetrics() {
        String sg = mindMapStore.createSubgraph(new SubgraphInput("test", SubgraphTypes.GENERAL, null), TENANT);
        mindMapStore.addNode(NodeInput.of("Alice", sg), TENANT);
        mindMapStore.addNode(NodeInput.of("Bob", sg), TENANT);

        GraphAnalyticsResult result = service.analytics(TENANT, sg, 2, 30, 0.3);

        assertThat(result).isNotNull();
        assertThat(result.orphanNodes()).hasSize(2);
        assertThat(result.density()).isNotNull();
    }

    @Test
    void affect_noMemoryStore_returnsNull() {
        CognitionService noMemory = new CognitionService(mindMapStore, null, null, null, null, null);
        assertThat(noMemory.affect(TENANT, null, null, null)).isNull();
    }

    @Test
    void attention_returnsNull_noAccumulator() {
        assertThat(service.attention(TENANT, "agent-1", 5)).isNull();
    }

    @Test
    void domainActivation_noDomainActivation_returnsNull() {
        assertThat(service.domainActivation(TENANT, "agent-1", "sg1", "sg2", null, null)).isNull();
    }

    @Test
    void activities_noActivityService_returnsEmpty() {
        assertThat(service.activities(TENANT, "withPerson", "Alice", null, 10)).isEmpty();
    }
}
