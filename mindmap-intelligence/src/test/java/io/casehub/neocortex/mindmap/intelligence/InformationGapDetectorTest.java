package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InformationGapDetectorTest {

    private InMemoryMindMapStore store;
    private InformationGapDetector detector;
    private static final String TENANT = "test-tenant";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        detector = new InformationGapDetector(store);
    }

    @Test
    void emptyGraph_returnsNoGaps() {
        var gaps = detector.detectGaps(TENANT, null);
        assertThat(gaps).isEmpty();
    }

    @Test
    void highConfidenceNode_noGap() {
        String sgId = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        store.addNode(NodeInput.of("known-thing", sgId)
                .withConfidence(Confidence.stated(0.9, NOW)), TENANT);

        var gaps = detector.detectGaps(TENANT, null);
        assertThat(gaps).isEmpty();
    }

    @Test
    void moderateConfidenceWithEdges_partialKnowledge() {
        String sgId = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        String nodeA = store.addNode(NodeInput.of("partial-knowledge", sgId)
                .withConfidence(Confidence.inferred(0.4, NOW)), TENANT);
        String nodeB = store.addNode(NodeInput.of("related-concept", sgId)
                .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
        store.addEdge(EdgeInput.of(nodeA, nodeB, "relates-to"), TENANT);

        var gaps = detector.detectGaps(TENANT, null);
        assertThat(gaps).isNotEmpty();

        var gap = gaps.getFirst();
        assertThat(gap.gapType()).isEqualTo(InformationGapScore.GapType.PARTIAL_KNOWLEDGE);
        assertThat(gap.gapIntensity()).isGreaterThan(0.2);
        assertThat(gap.nodeName()).isEqualTo("partial-knowledge");
    }

    @Test
    void zeroConfidenceNoEdges_noGap() {
        String sgId = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        store.addNode(NodeInput.of("unknown-thing", sgId)
                .withConfidence(Confidence.speculated(0.05, NOW)), TENANT);

        var gaps = detector.detectGaps(TENANT, null);
        assertThat(gaps).isEmpty();
    }

    @Test
    void nearCompleteKnowledge_tipOfTongue() {
        String sgId = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        String main = store.addNode(NodeInput.of("almost-known", sgId)
                .withConfidence(Confidence.stated(0.7, NOW)), TENANT);
        for (int i = 0; i < 5; i++) {
            String ctx = store.addNode(NodeInput.of("ctx-" + i, sgId)
                    .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
            store.addEdge(EdgeInput.of(main, ctx, "relates-to"), TENANT);
        }

        var gaps = detector.detectGaps(TENANT, null);
        var tipGaps = gaps.stream()
                .filter(g -> g.nodeName().equals("almost-known"))
                .toList();
        assertThat(tipGaps).isNotEmpty();
        assertThat(tipGaps.getFirst().gapType())
                .isEqualTo(InformationGapScore.GapType.TIP_OF_TONGUE);
    }

    @Test
    void goalAdjacentNode_graduatedRelevance() {
        String sgConcept = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        String sgGoal = store.createSubgraph(
                new SubgraphInput("goals", SubgraphTypes.GOAL, null), TENANT);

        String goalNode = store.addNode(NodeInput.of("my-goal", sgGoal), TENANT);
        String gapNode = store.addNode(NodeInput.of("near-goal", sgConcept)
                .withConfidence(Confidence.inferred(0.4, NOW)), TENANT);
        store.addEdge(EdgeInput.of(goalNode, gapNode, "requires"), TENANT);

        var gaps = detector.detectGaps(TENANT, Set.of(goalNode));
        var nearGoal = gaps.stream()
                .filter(g -> g.nodeName().equals("near-goal"))
                .findFirst();
        assertThat(nearGoal).isPresent();
        assertThat(nearGoal.get().goalRelevance()).isEqualTo(1.0);
        assertThat(nearGoal.get().gapType())
                .isEqualTo(InformationGapScore.GapType.PARTIAL_KNOWLEDGE);
    }

    @Test
    void goalProximity_twoHops_reducedRelevance() {
        String sgConcept = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        String sgGoal = store.createSubgraph(
                new SubgraphInput("goals", SubgraphTypes.GOAL, null), TENANT);

        String goalNode = store.addNode(NodeInput.of("my-goal", sgGoal), TENANT);
        String bridge = store.addNode(NodeInput.of("bridge", sgConcept)
                .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
        String farNode = store.addNode(NodeInput.of("two-hops-away", sgConcept)
                .withConfidence(Confidence.inferred(0.4, NOW)), TENANT);
        store.addEdge(EdgeInput.of(goalNode, bridge, "requires"), TENANT);
        store.addEdge(EdgeInput.of(bridge, farNode, "relates-to"), TENANT);

        var gaps = detector.detectGaps(TENANT, Set.of(goalNode));
        var farGap = gaps.stream()
                .filter(g -> g.nodeName().equals("two-hops-away"))
                .findFirst();
        assertThat(farGap).isPresent();
        assertThat(farGap.get().goalRelevance()).isEqualTo(0.7);
    }

    @Test
    void goalProximity_threeHops_minimalRelevance() {
        String sgConcept = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        String sgGoal = store.createSubgraph(
                new SubgraphInput("goals", SubgraphTypes.GOAL, null), TENANT);

        String goalNode = store.addNode(NodeInput.of("my-goal", sgGoal), TENANT);
        String b1 = store.addNode(NodeInput.of("bridge-1", sgConcept)
                .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
        String b2 = store.addNode(NodeInput.of("bridge-2", sgConcept)
                .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
        String farNode = store.addNode(NodeInput.of("three-hops-away", sgConcept)
                .withConfidence(Confidence.inferred(0.4, NOW)), TENANT);
        store.addEdge(EdgeInput.of(goalNode, b1, "requires"), TENANT);
        store.addEdge(EdgeInput.of(b1, b2, "relates-to"), TENANT);
        store.addEdge(EdgeInput.of(b2, farNode, "relates-to"), TENANT);

        var gaps = detector.detectGaps(TENANT, Set.of(goalNode));
        var farGap = gaps.stream()
                .filter(g -> g.nodeName().equals("three-hops-away"))
                .findFirst();
        assertThat(farGap).isPresent();
        assertThat(farGap.get().goalRelevance()).isEqualTo(0.4);
    }

    @Test
    void noGoals_zeroRelevanceForAll() {
        String sgId = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        String nodeA = store.addNode(NodeInput.of("topic-a", sgId)
                .withConfidence(Confidence.inferred(0.4, NOW)), TENANT);
        String nodeB = store.addNode(NodeInput.of("topic-b", sgId)
                .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
        store.addEdge(EdgeInput.of(nodeA, nodeB, "relates-to"), TENANT);

        var gaps = detector.detectGaps(TENANT, null);
        assertThat(gaps).allMatch(g -> g.goalRelevance() == 0.0);
    }

    @Test
    void goalBoost_reshapesRanking() {
        String sgConcept = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        String sgGoal = store.createSubgraph(
                new SubgraphInput("goals", SubgraphTypes.GOAL, null), TENANT);

        String goalNode = store.addNode(NodeInput.of("my-goal", sgGoal), TENANT);

        // Low-entropy gap: near goal
        String nearGoal = store.addNode(NodeInput.of("near-goal", sgConcept)
                .withConfidence(Confidence.stated(0.7, NOW)), TENANT);
        String ctx1 = store.addNode(NodeInput.of("ctx-1", sgConcept)
                .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
        store.addEdge(EdgeInput.of(nearGoal, ctx1, "relates-to"), TENANT);
        store.addEdge(EdgeInput.of(goalNode, nearGoal, "requires"), TENANT);

        // High-entropy gap: far from goal
        String farGap = store.addNode(NodeInput.of("far-gap", sgConcept)
                .withConfidence(Confidence.inferred(0.45, NOW)), TENANT);
        String ctx2 = store.addNode(NodeInput.of("ctx-2", sgConcept)
                .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
        store.addEdge(EdgeInput.of(farGap, ctx2, "relates-to"), TENANT);

        var gapsWithGoals = detector.detectGaps(TENANT, Set.of(goalNode));
        var nearGapScore = gapsWithGoals.stream()
                .filter(g -> g.nodeName().equals("near-goal")).findFirst();
        var farGapScore = gapsWithGoals.stream()
                .filter(g -> g.nodeName().equals("far-gap")).findFirst();

        assertThat(nearGapScore).isPresent();
        assertThat(farGapScore).isPresent();
        assertThat(nearGapScore.get().gapIntensity())
                .isGreaterThan(farGapScore.get().gapIntensity());
    }

    @Test
    void gapsSortedByIntensityDescending() {
        String sgId = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        store.addNode(NodeInput.of("low-gap", sgId)
                .withConfidence(Confidence.stated(0.75, NOW)), TENANT);
        String highGap = store.addNode(NodeInput.of("high-gap", sgId)
                .withConfidence(Confidence.inferred(0.35, NOW)), TENANT);
        String ctx = store.addNode(NodeInput.of("context", sgId)
                .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
        store.addEdge(EdgeInput.of(highGap, ctx, "relates-to"), TENANT);

        var gaps = detector.detectGaps(TENANT, null);
        if (gaps.size() >= 2) {
            assertThat(gaps.get(0).gapIntensity())
                    .isGreaterThanOrEqualTo(gaps.get(1).gapIntensity());
        }
    }

    @Test
    void loewensteinNonLinearity_peaksAtModerateConfidence() {
        double lowEntropy = InformationGapDetector.computeEntropy(0.05);
        double midEntropy = InformationGapDetector.computeEntropy(0.5);
        double highEntropy = InformationGapDetector.computeEntropy(0.95);

        assertThat(midEntropy).isGreaterThan(lowEntropy);
        assertThat(midEntropy).isGreaterThan(highEntropy);
        assertThat(midEntropy).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void typeSystemSubgraph_excluded() {
        String sgId = store.createSubgraph(
                new SubgraphInput("types", SubgraphTypes.TYPE_SYSTEM, null), TENANT);
        store.addNode(NodeInput.of("some-type", sgId)
                .withConfidence(Confidence.inferred(0.4, NOW)), TENANT);

        var gaps = detector.detectGaps(TENANT, null);
        assertThat(gaps).isEmpty();
    }

    @Test
    void connectedUnknown_lowConfidenceWithEdges() {
        String sgId = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        String unknown = store.addNode(NodeInput.of("barely-known", sgId)
                .withConfidence(Confidence.speculated(0.08, NOW)), TENANT);
        String known = store.addNode(NodeInput.of("well-known", sgId)
                .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
        store.addEdge(EdgeInput.of(unknown, known, "related-to"), TENANT);

        var gaps = detector.detectGaps(TENANT, null);
        var connectedGaps = gaps.stream()
                .filter(g -> g.nodeName().equals("barely-known"))
                .toList();
        assertThat(connectedGaps).isNotEmpty();
        assertThat(connectedGaps.getFirst().gapType())
                .isEqualTo(InformationGapScore.GapType.CONNECTED_UNKNOWN);
    }
}
