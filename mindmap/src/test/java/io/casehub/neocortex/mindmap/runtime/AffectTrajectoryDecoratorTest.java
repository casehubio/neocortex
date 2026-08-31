package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.mood.AffectEvents;
import io.casehub.neocortex.memory.mood.AffectRecorded;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class AffectTrajectoryDecoratorTest {

    private static final String TENANT = "test-tenant";
    private static final String SUBGRAPH = "test-sg";
    private static final Confidence CONF = new Confidence(ConfidenceOrigin.STATED, 0.9, Instant.now());

    private InMemoryMindMapStore delegate;
    private StubMemoryStore memoryStore;
    private List<AffectRecorded> firedEvents;
    private AffectTrajectoryDecorator decorator;

    @BeforeEach
    void setUp() {
        delegate = new InMemoryMindMapStore();
        memoryStore = new StubMemoryStore();
        firedEvents = new ArrayList<>();
        decorator = new AffectTrajectoryDecorator(delegate, memoryStore, firedEvents::add);
    }

    @Test
    void updateNode_padChange_storesAffectEntry() {
        String nodeId = decorator.addNode(
            new NodeInput("alice", SUBGRAPH, CONF, null, null, null, null, null, 0.5, 0.3, 0.2, Map.of()), TENANT);

        decorator.updateNode(nodeId, new NodeUpdate(null, null, null, null, null, null, null, null,
            0.8, -0.1, 0.5, null, null), TENANT);

        assertThat(memoryStore.stored).hasSize(1);
        MemoryInput stored = memoryStore.stored.getFirst();
        assertThat(stored.domain()).isEqualTo(AffectEvents.DOMAIN);
        assertThat(stored.entityId()).isEqualTo(nodeId);
        assertThat(stored.pleasure()).isEqualTo(0.8);
        assertThat(stored.arousal()).isEqualTo(-0.1);
        assertThat(stored.dominance()).isEqualTo(0.5);
    }

    @Test
    void updateNode_noPadChange_doesNotStore() {
        String nodeId = decorator.addNode(
            new NodeInput("bob", SUBGRAPH, CONF, null, null, null, null, null, 0.5, 0.3, 0.2, Map.of()), TENANT);

        decorator.updateNode(nodeId, new NodeUpdate("renamed", null, null, null, null, null, null, null,
            null, null, null, null, null), TENANT);

        assertThat(memoryStore.stored).isEmpty();
    }

    @Test
    void updateNode_padChange_firesEvent() {
        String nodeId = decorator.addNode(
            new NodeInput("carol", SUBGRAPH, CONF, null, null, null, null, null, null, null, null, Map.of()), TENANT);

        decorator.updateNode(nodeId, new NodeUpdate(null, null, null, null, null, null, null, null,
            0.2, 0.4, 0.6, null, null), TENANT);

        assertThat(firedEvents).hasSize(1);
        assertThat(firedEvents.getFirst().nodeId()).isEqualTo(nodeId);
        assertThat(firedEvents.getFirst().tenantId()).isEqualTo(TENANT);
    }

    @Test
    void updateNode_noMemoryStore_silentlySkips() {
        var decoratorNoMemory = new AffectTrajectoryDecorator(delegate, null, firedEvents::add);
        String nodeId = decoratorNoMemory.addNode(
            new NodeInput("dave", SUBGRAPH, CONF, null, null, null, null, null, 0.1, 0.1, 0.1, Map.of()), TENANT);

        decoratorNoMemory.updateNode(nodeId, new NodeUpdate(null, null, null, null, null, null, null, null,
            0.9, 0.9, 0.9, null, null), TENANT);

        assertThat(memoryStore.stored).isEmpty();
    }

    @Test
    void updateNode_samePadValues_doesNotStore() {
        String nodeId = decorator.addNode(
            new NodeInput("eve", SUBGRAPH, CONF, null, null, null, null, null, 0.5, 0.3, 0.2, Map.of()), TENANT);

        decorator.updateNode(nodeId, new NodeUpdate(null, null, null, null, null, null, null, null,
            0.5, 0.3, 0.2, null, null), TENANT);

        assertThat(memoryStore.stored).isEmpty();
    }

    static class StubMemoryStore implements CaseMemoryStore {
        final List<MemoryInput> stored = new CopyOnWriteArrayList<>();

        @Override
        public String store(MemoryInput input) {
            stored.add(input);
            return UUID.randomUUID().toString();
        }

        @Override
        public List<Memory> query(MemoryQuery query) { return List.of(); }

        @Override
        public int erase(EraseRequest request) { return 0; }
    }
}
