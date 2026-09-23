package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.cognitive.ModulationProfile;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GoalRelevanceModulationFactorTest {

    private InMemoryMindMapStore store;
    private String goalSgId;
    private String conceptSgId;
    private static final String TENANT = "t1";
    private static final ModulationProfile<Memory> PROFILE = ModulationProfiles.MEMORY;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        goalSgId = store.createSubgraph(
                new SubgraphInput("Goals", SubgraphTypes.GOAL, null), TENANT);
        conceptSgId = store.createSubgraph(
                new SubgraphInput("Concepts", SubgraphTypes.CONCEPT, null), TENANT);
    }

    private Memory memory(String entityId) {
        return new Memory("m1", Subject.of("concept", entityId),
                new MemoryDomain("experience"), TENANT, null,
                "test", Map.of(), Instant.now(),
                new Confidence(ConfidenceOrigin.STATED, 0.8, null),
                null, null, null, null, Set.of());
    }

    @Test
    void directEdge_returnsFullWeight() {
        String goalId = store.addNode(NodeInput.of("Learn ML", goalSgId)
                .withProperties(Map.of("description", "learn ML", "status", "active")), TENANT);
        String entityId = store.addNode(NodeInput.of("ML-entity", conceptSgId)
                .withProperties(Map.of()), TENANT);
        store.addEdge(EdgeInput.of(entityId, goalId, "contributes-to"), TENANT);

        var factor = new GoalRelevanceModulationFactor(store, TENANT);
        double weight = factor.apply(memory("ML-entity"), PROFILE);
        assertThat(weight).isEqualTo(1.0);
    }

    @Test
    void twoEdgeProximity_returnsDecayedWeight() {
        String goalId = store.addNode(NodeInput.of("Learn ML", goalSgId)
                .withProperties(Map.of("description", "learn ML", "status", "active")), TENANT);
        String bridgeId = store.addNode(NodeInput.of("Bridge", conceptSgId)
                .withProperties(Map.of()), TENANT);
        String entityId = store.addNode(NodeInput.of("Entity", conceptSgId)
                .withProperties(Map.of()), TENANT);
        store.addEdge(EdgeInput.of(bridgeId, goalId, "contributes-to"), TENANT);
        store.addEdge(EdgeInput.of(entityId, bridgeId, "enables"), TENANT);

        var factor = new GoalRelevanceModulationFactor(store, TENANT);
        double weight = factor.apply(memory("Entity"), PROFILE);
        assertThat(weight).isEqualTo(0.7);
    }

    @Test
    void noEntityNode_returnsNeutral() {
        store.addNode(NodeInput.of("Learn ML", goalSgId)
                .withProperties(Map.of("description", "learn ML", "status", "active")), TENANT);

        var factor = new GoalRelevanceModulationFactor(store, TENANT);
        double weight = factor.apply(memory("nonexistent"), PROFILE);
        assertThat(weight).isEqualTo(1.0);
    }

    @Test
    void noActiveGoals_returnsNeutral() {
        store.addNode(NodeInput.of("Done goal", goalSgId)
                .withProperties(Map.of("description", "done", "status", "completed")), TENANT);

        var factor = new GoalRelevanceModulationFactor(store, TENANT);
        double weight = factor.apply(memory("some-entity"), PROFILE);
        assertThat(weight).isEqualTo(1.0);
    }

    @Test
    void fourPlusEdges_returnsZero() {
        String goalId = store.addNode(NodeInput.of("Goal", goalSgId)
                .withProperties(Map.of("description", "goal", "status", "active")), TENANT);
        String n1 = store.addNode(NodeInput.of("N1", conceptSgId).withProperties(Map.of()), TENANT);
        String n2 = store.addNode(NodeInput.of("N2", conceptSgId).withProperties(Map.of()), TENANT);
        String n3 = store.addNode(NodeInput.of("N3", conceptSgId).withProperties(Map.of()), TENANT);
        String entity = store.addNode(NodeInput.of("Far", conceptSgId).withProperties(Map.of()), TENANT);
        store.addEdge(EdgeInput.of(n1, goalId, "enables"), TENANT);
        store.addEdge(EdgeInput.of(n2, n1, "enables"), TENANT);
        store.addEdge(EdgeInput.of(n3, n2, "enables"), TENANT);
        store.addEdge(EdgeInput.of(entity, n3, "enables"), TENANT);

        var factor = new GoalRelevanceModulationFactor(store, TENANT);
        double weight = factor.apply(memory("Far"), PROFILE);
        assertThat(weight).isEqualTo(0.0);
    }
}
