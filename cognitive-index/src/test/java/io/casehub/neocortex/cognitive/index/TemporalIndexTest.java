package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.mindmap.NodeInput;
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

class TemporalIndexTest {

    private static final String TENANT = "test-tenant";
    private static final String AGENT = "agent-1";
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Instant HOUR_AGO = NOW.minusSeconds(3600);
    private static final Instant TOMORROW = NOW.plusSeconds(86400);
    private static final MemoryDomain EXPERIENCE = new MemoryDomain("experience");
    private static final Confidence CONF = new Confidence(ConfidenceOrigin.STATED, 0.8, NOW);

    private InMemoryMindMapStore mindMapStore;
    private StubMemoryStore memoryStore;
    private TemporalIndex index;

    @BeforeEach
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        memoryStore = new StubMemoryStore();
        index = new TemporalIndex(mindMapStore, memoryStore, null);
    }

    @Test
    void since_returnsMindMapNodesByUpdatedAt() {
        mindMapStore.addNode(node("meeting"), TENANT);

        var query = TemporalQuery.since(List.of(TENANT), HOUR_AGO, 50);
        var results = index.query(query);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().source()).isInstanceOf(TemporalSource.FromMindMap.class);
        assertThat(results.getFirst().tenantId()).isEqualTo(TENANT);
    }

    @Test
    void since_returnsMemoriesWithEntityIds() {
        memoryStore.store(new MemoryInput(AGENT, EXPERIENCE, TENANT, null, "test fact", Map.of(), CONF, null, null, null));

        var query = TemporalQuery.since(List.of(TENANT), HOUR_AGO, 50)
            .withEntityIds(List.of(AGENT));
        var results = index.query(query);

        assertThat(results)
            .anyMatch(e -> e.source() instanceof TemporalSource.FromMemory);
    }

    @Test
    void since_skipsMemoryWhenNoEntityIds() {
        memoryStore.store(new MemoryInput(AGENT, EXPERIENCE, TENANT, null, "test fact", Map.of(), CONF, null, null, null));

        var query = TemporalQuery.since(List.of(TENANT), HOUR_AGO, 50);
        var results = index.query(query);

        assertThat(results)
            .noneMatch(e -> e.source() instanceof TemporalSource.FromMemory);
    }

    @Test
    void upcoming_returnsFutureMindMapNodes() {
        mindMapStore.addNode(nodeWithValidFrom("future event", TOMORROW), TENANT);

        var query = TemporalQuery.upcoming(List.of(TENANT), NOW, 50);
        var results = index.query(query);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().timestamp()).isEqualTo(TOMORROW);
    }

    @Test
    void multiTenant_mergesResultsWithProvenance() {
        mindMapStore.addNode(node("node-t1"), "tenant-1");
        mindMapStore.addNode(node("node-t2"), "tenant-2");

        var query = TemporalQuery.since(List.of("tenant-1", "tenant-2"), HOUR_AGO, 50);
        var results = index.query(query);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(TemporalEntry::tenantId)
            .containsExactlyInAnyOrder("tenant-1", "tenant-2");
    }

    @Test
    void resultsSortedChronologically() {
        memoryStore.store(new MemoryInput(AGENT, EXPERIENCE, TENANT, null, "first", Map.of(), CONF, null, null, null));
        memoryStore.store(new MemoryInput(AGENT, EXPERIENCE, TENANT, null, "second", Map.of(), CONF, null, null, null));
        mindMapStore.addNode(node("node"), TENANT);

        var query = TemporalQuery.since(List.of(TENANT), HOUR_AGO, 50)
            .withEntityIds(List.of(AGENT));
        var results = index.query(query);

        assertThat(results).isSortedAccordingTo(TemporalEntry::compareTo);
    }

    @Test
    void limit_trimsMergedResults() {
        for (int i = 0; i < 5; i++) {
            memoryStore.store(new MemoryInput(AGENT, EXPERIENCE, TENANT, null, "fact-" + i, Map.of(), CONF, null, null, null));
        }
        var query = TemporalQuery.since(List.of(TENANT), HOUR_AGO, 3)
            .withEntityIds(List.of(AGENT));
        var results = index.query(query);

        assertThat(results).hasSize(3);
    }

    @Test
    void emptyWindow_returnsNoResults() {
        mindMapStore.addNode(node("node"), TENANT);

        var farFuture = Instant.parse("2099-01-01T00:00:00Z");
        var query = TemporalQuery.window(List.of(TENANT), farFuture, farFuture.plusSeconds(3600), 50);
        var results = index.query(query);

        assertThat(results).isEmpty();
    }

    @Test
    void withSources_onlyQueriesRequestedStores() {
        memoryStore.store(new MemoryInput(AGENT, EXPERIENCE, TENANT, null, "fact", Map.of(), CONF, null, null, null));
        mindMapStore.addNode(node("node"), TENANT);

        var query = TemporalQuery.since(List.of(TENANT), HOUR_AGO, 50)
            .withEntityIds(List.of(AGENT))
            .withSources(Set.of(TemporalQuery.StoreKind.MEMORY));
        var results = index.query(query);

        assertThat(results)
            .allMatch(e -> e.source() instanceof TemporalSource.FromMemory);
    }

    @Test
    void missingStore_silentlySkipped() {
        var indexNoMemory = new TemporalIndex(mindMapStore, null, null);
        mindMapStore.addNode(node("node"), TENANT);

        var query = TemporalQuery.since(List.of(TENANT), HOUR_AGO, 50);
        var results = indexNoMemory.query(query);

        assertThat(results).hasSize(1);
    }

    private static final String SUBGRAPH = "test-subgraph";

    private static NodeInput node(String name) {
        return new NodeInput(name, SUBGRAPH, CONF, null, null, null, null, null, null, null, null, Map.of());
    }

    private static NodeInput nodeWithValidFrom(String name, Instant validFrom) {
        return new NodeInput(name, SUBGRAPH, CONF, null, null, null, validFrom, null, null, null, null, Map.of());
    }

    /**
     * Minimal CaseMemoryStore stub for testing TemporalIndex without CDI
     * or platform dependencies. Stores memories in a list, filters by
     * entityId, domain, tenantId, and since.
     */
    static class StubMemoryStore implements CaseMemoryStore {
        private final List<Memory> memories = new CopyOnWriteArrayList<>();

        @Override
        public String store(MemoryInput input) {
            String id = UUID.randomUUID().toString();
            memories.add(new Memory(id, input.entityId(), input.domain(), input.tenantId(),
                                    input.caseId(), input.text(), input.attributes(), Instant.now(), input.confidence(), null, null, null));
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
