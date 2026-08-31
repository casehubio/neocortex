package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeRef;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveProfileTest {

    private static final String TENANT = "test-tenant";
    private static final String SUBGRAPH = "test-subgraph";
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Confidence CONF = new Confidence(ConfidenceOrigin.STATED, 0.8, NOW);
    private static final MemoryDomain EXPERIENCE = new MemoryDomain("experience");
    private static final MemoryDomain RELATIONSHIP = new MemoryDomain("relationship");
    private static final MemoryDomain AFFECT = new MemoryDomain("affect");

    private InMemoryMindMapStore mindMapStore;
    private TestMemoryStore memoryStore;
    private CognitiveProfile profile;

    @BeforeEach
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        memoryStore = new TestMemoryStore();
        profile = new CognitiveProfile(mindMapStore, memoryStore, null);
        mindMapStore.createSubgraph(new SubgraphInput(SUBGRAPH, null, null), TENANT);
    }

    @Test
    void resolveById_returnsEntityKnowledge() {
        String nodeId = mindMapStore.addNode(node("Alice"), TENANT);

        var result = profile.resolve(CognitiveProfileQuery.byId(nodeId, TENANT));

        assertThat(result).isPresent();
        assertThat(result.get().node().name()).isEqualTo("Alice");
        assertThat(result.get().tenantId()).isEqualTo(TENANT);
    }

    @Test
    void resolveByName_returnsEntityKnowledge() {
        mindMapStore.addNode(node("Alice"), TENANT);

        var result = profile.resolve(CognitiveProfileQuery.byName("Alice", SUBGRAPH, TENANT));

        assertThat(result).isPresent();
        assertThat(result.get().node().name()).isEqualTo("Alice");
    }

    @Test
    void resolveById_notFound_returnsEmpty() {
        var result = profile.resolve(CognitiveProfileQuery.byId("nonexistent", TENANT));
        assertThat(result).isEmpty();
    }

    @Test
    void resolveByName_notFound_returnsEmpty() {
        var result = profile.resolve(CognitiveProfileQuery.byName("Nobody", SUBGRAPH, TENANT));
        assertThat(result).isEmpty();
    }

    @Test
    void includesDirectEdges() {
        String aliceId = mindMapStore.addNode(node("Alice"), TENANT);
        String bobId = mindMapStore.addNode(node("Bob"), TENANT);
        mindMapStore.addEdge(new EdgeInput(aliceId, bobId, "knows", CONF, null, null, null, null, null, null, Map.of()), TENANT);

        var result = profile.resolve(CognitiveProfileQuery.byId(aliceId, TENANT));

        assertThat(result).isPresent();
        assertThat(result.get().edges()).hasSize(1);
        assertThat(result.get().edges().getFirst().edgeType()).isEqualTo("knows");
    }

    @Test
    void excludesEdges_whenIncludeEdgesFalse() {
        String aliceId = mindMapStore.addNode(node("Alice"), TENANT);
        String bobId = mindMapStore.addNode(node("Bob"), TENANT);
        mindMapStore.addEdge(new EdgeInput(aliceId, bobId, "knows", CONF, null, null, null, null, null, null, Map.of()), TENANT);

        var query = CognitiveProfileQuery.byId(aliceId, TENANT).withIncludeEdges(false);
        var result = profile.resolve(query);

        assertThat(result).isPresent();
        assertThat(result.get().edges()).isEmpty();
    }

    @Test
    void queriesMemoriesByDomain() {
        String nodeId = mindMapStore.addNode(node("Alice"), TENANT);
        memoryStore.store(new MemoryInput(nodeId, EXPERIENCE, TENANT, null, "Alice had an experience", Map.of(), CONF, null, null, null));
        memoryStore.store(new MemoryInput("Alice", RELATIONSHIP, TENANT, null, "Alice relates to Bob", Map.of(), CONF, null, null, null));

        var result = profile.resolve(CognitiveProfileQuery.byId(nodeId, TENANT));

        assertThat(result).isPresent();
        assertThat(result.get().memories()).containsKey(EXPERIENCE);
        assertThat(result.get().memories()).containsKey(RELATIONSHIP);
        assertThat(result.get().memories().get(EXPERIENCE)).hasSize(1);
        assertThat(result.get().memories().get(RELATIONSHIP)).hasSize(1);
    }

    @Test
    void domainSelection_onlyQueriesRequestedDomains() {
        String nodeId = mindMapStore.addNode(node("Alice"), TENANT);
        memoryStore.store(new MemoryInput(nodeId, EXPERIENCE, TENANT, null, "experience fact", Map.of(), CONF, null, null, null));
        memoryStore.store(new MemoryInput(nodeId, RELATIONSHIP, TENANT, null, "relationship fact", Map.of(), CONF, null, null, null));

        var query = CognitiveProfileQuery.byId(nodeId, TENANT)
            .withDomains(Set.of(EXPERIENCE));
        var result = profile.resolve(query);

        assertThat(result).isPresent();
        assertThat(result.get().memories()).containsKey(EXPERIENCE);
        assertThat(result.get().memories()).doesNotContainKey(RELATIONSHIP);
    }

    @Test
    void dualEntityId_findsMemoriesStoredUnderBothNodeIdAndName() {
        String nodeId = mindMapStore.addNode(node("Alice"), TENANT);
        memoryStore.store(new MemoryInput(nodeId, EXPERIENCE, TENANT, null, "stored by nodeId", Map.of(), CONF, null, null, null));
        memoryStore.store(new MemoryInput("Alice", EXPERIENCE, TENANT, null, "stored by name", Map.of(), CONF, null, null, null));

        var result = profile.resolve(CognitiveProfileQuery.byId(nodeId, TENANT));

        assertThat(result).isPresent();
        assertThat(result.get().memories().get(EXPERIENCE)).hasSize(2);
    }

    @Test
    void computesAffectTrajectory() {
        String nodeId = mindMapStore.addNode(node("Alice"), TENANT);
        memoryStore.store(new MemoryInput(nodeId, AFFECT, TENANT, null, "PAD update", Map.of(), CONF, -0.5, 0.3, 0.1));
        memoryStore.store(new MemoryInput(nodeId, AFFECT, TENANT, null, "PAD update", Map.of(), CONF, -0.7, 0.5, -0.1));

        var result = profile.resolve(CognitiveProfileQuery.byId(nodeId, TENANT));

        assertThat(result).isPresent();
        assertThat(result.get().trajectory()).isNotNull();
        assertThat(result.get().trajectory().trend()).isEqualTo(TrendDirection.WORSENING);
        assertThat(result.get().trajectory().sampleCount()).isEqualTo(2);
    }

    @Test
    void trajectoryComputed_evenWhenAffectDomainNotRequested() {
        String nodeId = mindMapStore.addNode(node("Alice"), TENANT);
        memoryStore.store(new MemoryInput(nodeId, AFFECT, TENANT, null, "PAD update", Map.of(), CONF, 0.5, 0.3, 0.1));
        memoryStore.store(new MemoryInput(nodeId, AFFECT, TENANT, null, "PAD update", Map.of(), CONF, 0.7, 0.2, 0.2));

        var query = CognitiveProfileQuery.byId(nodeId, TENANT)
            .withDomains(Set.of(EXPERIENCE));
        var result = profile.resolve(query);

        assertThat(result).isPresent();
        assertThat(result.get().trajectory()).isNotNull();
        assertThat(result.get().memories()).doesNotContainKey(AFFECT);
    }

    @Test
    void nodeRefMemory_followsSchemeMemory() {
        String linkedEntityId = "external-entity-123";
        var refs = Set.of(new NodeRef("memory", linkedEntityId, null));
        String nodeId = mindMapStore.addNode(nodeWithRefs("Alice", refs), TENANT);
        memoryStore.store(new MemoryInput(linkedEntityId, EXPERIENCE, TENANT, null, "linked memory", Map.of(), CONF, null, null, null));

        var result = profile.resolve(CognitiveProfileQuery.byId(nodeId, TENANT));

        assertThat(result).isPresent();
        assertThat(result.get().memories().get(EXPERIENCE))
            .anyMatch(m -> m.text().equals("linked memory"));
    }

    @Test
    void nodeRefCbr_recordedAsUnresolved() {
        var refs = Set.of(new NodeRef("cbr", "case-42", null));
        String nodeId = mindMapStore.addNode(nodeWithRefs("Alice", refs), TENANT);

        var result = profile.resolve(CognitiveProfileQuery.byId(nodeId, TENANT));

        assertThat(result).isPresent();
        assertThat(result.get().unresolvedRefs())
            .containsExactly(new NodeRef("cbr", "case-42", null));
    }

    @Test
    void gracefulDegradation_noMemoryStore() {
        var profileNoMemory = new CognitiveProfile(mindMapStore, null, null);
        String nodeId = mindMapStore.addNode(node("Alice"), TENANT);

        var result = profileNoMemory.resolve(CognitiveProfileQuery.byId(nodeId, TENANT));

        assertThat(result).isPresent();
        assertThat(result.get().node().name()).isEqualTo("Alice");
        assertThat(result.get().memories()).isEmpty();
        assertThat(result.get().trajectory()).isNull();
    }

    @Test
    void gracefulDegradation_noMindMapStore() {
        var profileNoMindMap = new CognitiveProfile(null, memoryStore, null);

        var result = profileNoMindMap.resolve(CognitiveProfileQuery.byId("any-id", TENANT));

        assertThat(result).isEmpty();
    }

    @Test
    void memoryLimit_appliedPerDomain() {
        String nodeId = mindMapStore.addNode(node("Alice"), TENANT);
        for (int i = 0; i < 10; i++) {
            memoryStore.store(new MemoryInput(nodeId, EXPERIENCE, TENANT, null, "fact-" + i, Map.of(), CONF, null, null, null));
        }

        var query = CognitiveProfileQuery.byId(nodeId, TENANT).withMemoryLimit(3);
        var result = profile.resolve(query);

        assertThat(result).isPresent();
        assertThat(result.get().memories().get(EXPERIENCE)).hasSize(3);
    }

    @Test
    void emptyEntity_nodeExistsButNoMemoriesOrEdges() {
        String nodeId = mindMapStore.addNode(node("Alice"), TENANT);

        var result = profile.resolve(CognitiveProfileQuery.byId(nodeId, TENANT));

        assertThat(result).isPresent();
        assertThat(result.get().edges()).isEmpty();
        assertThat(result.get().memories()).isEmpty();
        assertThat(result.get().trajectory()).isNull();
        assertThat(result.get().unresolvedRefs()).isEmpty();
    }

    // --- helpers ---

    private static NodeInput node(String name) {
        return new NodeInput(name, SUBGRAPH, CONF, null, null, null, null, null, null, null, null, Map.of());
    }

    private static NodeInput nodeWithRefs(String name, Set<NodeRef> refs) {
        return new NodeInput(name, SUBGRAPH, CONF, null, null, refs, null, null, null, null, null, Map.of());
    }

    static class TestMemoryStore implements CaseMemoryStore {
        private final List<Memory>                           memories = new CopyOnWriteArrayList<>();
        private final java.util.concurrent.atomic.AtomicLong tick     = new java.util.concurrent.atomic.AtomicLong();

        @Override
        public String store(MemoryInput input) {
            String  id = UUID.randomUUID().toString();
            Instant ts = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(tick.getAndIncrement() * 3600);
            memories.add(new Memory(id, input.entityId(), input.domain(), input.tenantId(),
                                    input.caseId(), input.text(), input.attributes(), ts,
                                    input.confidence(), input.pleasure(), input.arousal(), input.dominance()));
            return id;
        }

        @Override
        public List<Memory> query(MemoryQuery query) {
            return memories.stream()
                           .filter(m -> query.entityIds().contains(m.entityId()))
                           .filter(m -> m.domain().equals(query.domain()))
                           .filter(m -> m.tenantId().equals(query.tenantId()))
                           .filter(m -> query.since() == null || !m.createdAt().isBefore(query.since()))
                           .limit(query.limit())
                           .toList();
        }

        @Override
        public int erase(EraseRequest request) {
            return 0;
        }
    }
}
