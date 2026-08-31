package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapVocabulary;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.OverlayRef;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphType;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PerspectivalResolverTest {

    private static final Instant    NOW    = Instant.parse("2026-06-01T12:00:00Z");
    private static final String     TENANT = "smiths-family";
    private static final Confidence CONF   =
            new Confidence(ConfidenceOrigin.STATED, 0.9, NOW);

    private MindMapStore         mindMapStore;
    private PerspectivalResolver resolver;
    private String               subgraphId;

    @BeforeEach
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        mindMapStore.registerVocabulary(MindMapVocabulary.builder()
                                                         .edgeType("related-to").build());

        resolver = new PerspectivalResolver(mindMapStore);

        subgraphId = mindMapStore.createSubgraph(
                new SubgraphInput("Family", SubgraphType.GENERAL, null), TENANT);
    }

    @Test
    void findsOverlayAndMerges() {
        String sharedId = addSharedNode("Grandma");
        addOverlay(sharedId, "alice", 0.9, 0.3, 0.5);
        MindMapNode shared = mindMapStore.getNode(sharedId, TENANT);

        var result = resolver.resolve(List.of(shared), "alice", TENANT);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Grandma");
        assertThat(result.getFirst().pleasure()).isEqualTo(0.9);
        assertThat(result.getFirst().arousal()).isEqualTo(0.3);
        assertThat(result.getFirst().dominance()).isEqualTo(0.5);
    }

    @Test
    void noOverlayReturnsSharedNodeAsIs() {
        String      sharedId = addSharedNode("Uncle Bob");
        MindMapNode shared   = mindMapStore.getNode(sharedId, TENANT);

        var result = resolver.resolve(List.of(shared), "alice", TENANT);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Uncle Bob");
        assertThat(result.getFirst().pleasure()).isNull();
    }

    @Test
    void mixedSomeWithOverlaysSomeWithout() {
        String grandmaId = addSharedNode("Grandma");
        String uncleId   = addSharedNode("Uncle Bob");
        addOverlay(grandmaId, "alice", 0.9, 0.3, 0.5);

        MindMapNode grandma = mindMapStore.getNode(grandmaId, TENANT);
        MindMapNode uncle   = mindMapStore.getNode(uncleId, TENANT);

        var result = resolver.resolve(List.of(grandma, uncle), "alice", TENANT);

        assertThat(result).hasSize(2);
        MindMapNode resolvedGrandma = result.stream()
                                            .filter(n -> n.name().equals("Grandma")).findFirst().orElseThrow();
        MindMapNode resolvedUncle = result.stream()
                                          .filter(n -> n.name().equals("Uncle Bob")).findFirst().orElseThrow();
        assertThat(resolvedGrandma.pleasure()).isEqualTo(0.9);
        assertThat(resolvedUncle.pleasure()).isNull();
    }

    @Test
    void overlayFilteredByAgentId() {
        String sharedId = addSharedNode("Grandma");
        addOverlay(sharedId, "alice", 0.9, 0.3, 0.5);
        addOverlay(sharedId, "bob", 0.1, 0.8, 0.2);
        MindMapNode shared = mindMapStore.getNode(sharedId, TENANT);

        var aliceResult = resolver.resolve(List.of(shared), "alice", TENANT);
        var bobResult   = resolver.resolve(List.of(shared), "bob", TENANT);

        assertThat(aliceResult.getFirst().pleasure()).isEqualTo(0.9);
        assertThat(bobResult.getFirst().pleasure()).isEqualTo(0.1);
    }

    @Test
    void gracefulDegradationNoMindMapStore() {
        PerspectivalResolver noStore = new PerspectivalResolver(
                (MindMapStore) null);
        MindMapNode shared = new StubNode("s1", "Test", "sg1",
                                          CONF, null, NOW, NOW, null, null,
                                          Set.of(), Set.of(), null, null, null, Map.of());

        var result = noStore.resolve(List.of(shared), "alice", TENANT);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().pleasure()).isNull();
    }

    @Test
    void multipleSharedNodesBatchedInOneQuery() {
        String id1 = addSharedNode("Grandma");
        String id2 = addSharedNode("Grandpa");
        addOverlay(id1, "alice", 0.9, 0.3, 0.5);
        addOverlay(id2, "alice", 0.7, 0.2, 0.4);

        MindMapNode n1 = mindMapStore.getNode(id1, TENANT);
        MindMapNode n2 = mindMapStore.getNode(id2, TENANT);

        var result = resolver.resolve(List.of(n1, n2), "alice", TENANT);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(n -> assertThat(n.pleasure()).isNotNull());
    }

    private String addSharedNode(String name) {
        return mindMapStore.addNode(new NodeInput(
                name, subgraphId, CONF, null,
                Set.of(), Set.of(), null, null,
                null, null, null, Map.of()), TENANT);
    }

    private void addOverlay(String sharedNodeId, String agentId,
                            double p, double a, double d) {
        mindMapStore.addNode(new NodeInput(
                "overlay-" + sharedNodeId + "-" + agentId, subgraphId, null, null,
                Set.of("overlay"), Set.of(OverlayRef.of(sharedNodeId)),
                null, null, p, a, d,
                Map.of(OverlayRef.AGENT_ID, agentId)), TENANT);
    }
}
