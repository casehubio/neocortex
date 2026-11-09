package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.platform.agent.session.SessionPipeline;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.mindmap.intelligence.InformationGapDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CuriosityDescriptionGenerationPhaseTest {

    private InMemoryMindMapStore store;
    private CuriosityDescriptionGenerationPhase phase;
    private static final String TENANT = "test-tenant";
    private static final String AGENT = "test-agent";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        var detector = new InformationGapDetector(store);
        var registry = CognitiveDefaultsRegistry.forTesting(CognitiveDefaults.empty(AGENT));
        phase = new CuriosityDescriptionGenerationPhase(store, detector, registry, (SessionPipeline) null);
    }

    @Test
    void noGaps_noNodeCreated() {
        phase.run(TENANT, List.of());

        var nodes = store.search(MindMapQuery.of(TENANT, 10)
                .withTraits(Set.of(CuriosityDescriptionGenerationPhase.GENERATED_TRAIT)));
        assertThat(nodes).isEmpty();
    }

    @Test
    void gapsPresent_createsFallbackDescription() {
        String sgId = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        String nodeA = store.addNode(NodeInput.of("quantum-theory", sgId)
                .withConfidence(Confidence.inferred(0.4, NOW)), TENANT);
        String nodeB = store.addNode(NodeInput.of("physics", sgId)
                .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
        store.addEdge(EdgeInput.of(nodeA, nodeB, "relates-to"), TENANT);

        phase.run(TENANT, List.of());

        var nodes = store.search(MindMapQuery.of(TENANT, 10)
                .withTraits(Set.of(CuriosityDescriptionGenerationPhase.GENERATED_TRAIT)));
        assertThat(nodes).hasSize(1);

        var node = nodes.getFirst();
        assertThat(node.traits()).contains(CuriosityDescriptionGenerationPhase.GENERATED_TRAIT);
        assertThat(node.property("agent-id")).hasValue(AGENT);
        assertThat(node.property("description")).isPresent();
        assertThat(node.property("description").get()).isNotBlank();
    }

    @Test
    void subsequentRun_updatesExistingNode() {
        String sgId = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        String nodeA = store.addNode(NodeInput.of("topic", sgId)
                .withConfidence(Confidence.inferred(0.4, NOW)), TENANT);
        String nodeB = store.addNode(NodeInput.of("context", sgId)
                .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
        store.addEdge(EdgeInput.of(nodeA, nodeB, "relates-to"), TENANT);

        phase.run(TENANT, List.of());
        phase.run(TENANT, List.of());

        var nodes = store.search(MindMapQuery.of(TENANT, 10)
                .withTraits(Set.of(CuriosityDescriptionGenerationPhase.GENERATED_TRAIT)));
        assertThat(nodes).hasSize(1);
    }

    @Test
    void fallbackDescription_containsChannelContent() {
        String sgId = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        String nodeA = store.addNode(NodeInput.of("mystery", sgId)
                .withConfidence(Confidence.inferred(0.4, NOW)), TENANT);
        String nodeB = store.addNode(NodeInput.of("clue", sgId)
                .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
        store.addEdge(EdgeInput.of(nodeA, nodeB, "relates-to"), TENANT);

        phase.run(TENANT, List.of());

        var description = store.search(MindMapQuery.of(TENANT, 10)
                        .withTraits(Set.of(CuriosityDescriptionGenerationPhase.GENERATED_TRAIT)))
                .getFirst().property("description").orElse("");
        // Fallback with default deprivation bias (0.5) = BLENDED hedonic
        assertThat(description).isNotBlank();
        // Should contain focus channel text
        assertThat(description).containsAnyOf("question", "exploring");
    }

    @Test
    void name_isCuriosityDescription() {
        assertThat(phase.name()).isEqualTo("curiosity-description");
    }
}
