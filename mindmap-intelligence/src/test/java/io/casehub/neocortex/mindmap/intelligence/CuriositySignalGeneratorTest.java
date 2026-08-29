package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphType;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CuriositySignalGeneratorTest {

    private static final String TENANT = "test-tenant";
    private InMemoryMindMapStore store;
    private CuriositySignalGenerator generator;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        generator = new CuriositySignalGenerator(store);
    }

    @Test
    void emptyGraphProducesNoSignals() {
        List<CuriositySignal> signals = generator.computeSignals(TENANT, Set.of());
        assertThat(signals).isEmpty();
    }

    @Test
    void orphanNodeProducesStructuralSignal() {
        String sgId = store.createSubgraph(new SubgraphInput("People", SubgraphType.PERSON, null), TENANT);
        store.addNode(new NodeInput("Alice", sgId, null, "test",
            null, null, null, null, null, null, null, Map.of()), TENANT);

        List<CuriositySignal> signals = generator.computeSignals(TENANT, Set.of());

        assertThat(signals).anyMatch(s ->
            s.category() == SignalCategory.STRUCTURAL
            && s.question().contains("Alice"));
    }

    @Test
    void contradictionProducesQualitySignal() {
        String sgId = store.createSubgraph(new SubgraphInput("People", SubgraphType.PERSON, null), TENANT);
        String aliceId = store.addNode(new NodeInput("Alice", sgId, null, "test",
                                                     null, null, null, null, null, null, null, Map.of()), TENANT);
        String acmeId = store.addNode(new NodeInput("Acme", sgId, null, "test",
                                                    null, null, null, null, null, null, null, Map.of()), TENANT);
        String initechId = store.addNode(new NodeInput("Initech", sgId, null, "test",
                                                       null, null, null, null, null, null, null, Map.of()), TENANT);
        store.addEdge(new EdgeInput(aliceId, acmeId, "works-at", null, "test",
                                    null, null, null, null, null, Map.of()), TENANT);
        store.addEdge(new EdgeInput(aliceId, initechId, "works-at", null, "test",
                                    null, null, null, null, null, Map.of()), TENANT);

        List<CuriositySignal> signals = generator.computeSignals(TENANT, Set.of());

        assertThat(signals).anyMatch(s ->
                                             s.category() == SignalCategory.QUALITY
                                             && s.description().contains("Contradiction"));
    }

    @Test
    void proximitySignalForFutureEvent() {
        String sgId = store.createSubgraph(new SubgraphInput("Events", SubgraphType.GENERAL, null), TENANT);
        Instant threeDaysFromNow = Instant.now().plus(3, ChronoUnit.DAYS);
        store.addNode(new NodeInput("Visit parents", sgId, null, "test",
            null, null, threeDaysFromNow, null, null, null, null, Map.of()), TENANT);

        List<CuriositySignal> signals = generator.computeSignals(TENANT, Set.of());

        assertThat(signals).anyMatch(s ->
            s.category() == SignalCategory.PROXIMITY
            && s.question().contains("Visit parents")
            && s.score() > 0.5);
    }

    @Test
    void pastEventProducesTemporalCheckSignal() {
        String sgId = store.createSubgraph(new SubgraphInput("Events", SubgraphType.GENERAL, null), TENANT);
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        store.addNode(new NodeInput("Meeting", sgId, null, "test",
            null, null, null, yesterday, null, null, null, Map.of()), TENANT);

        List<CuriositySignal> signals = generator.computeSignals(TENANT, Set.of());

        assertThat(signals).anyMatch(s ->
            s.category() == SignalCategory.TEMPORAL
            && s.question().contains("Meeting")
            && s.question().toLowerCase().contains("outcome"));
    }

    @Test
    void affectDampeningReducesScoreForNegativePleasure() {
        String sgId = store.createSubgraph(new SubgraphInput("People", SubgraphType.PERSON, null), TENANT);
        store.addNode(new NodeInput("Sad Topic", sgId, null, "test",
            null, null, null, null, -0.8, null, null, Map.of()), TENANT);

        List<CuriositySignal> signals = generator.computeSignals(TENANT, Set.of());

        CuriositySignal sadSignal = signals.stream()
            .filter(s -> s.description().contains("Sad Topic"))
            .findFirst().orElse(null);
        if (sadSignal != null) {
            assertThat(sadSignal.score()).isLessThanOrEqualTo(0.3);
        }
    }

    @Test
    void proximitySignalBypassesAffectDampening() {
        String sgId = store.createSubgraph(new SubgraphInput("Events", SubgraphType.GENERAL, null), TENANT);
        Instant twoDaysFromNow = Instant.now().plus(2, ChronoUnit.DAYS);
        store.addNode(new NodeInput("Funeral", sgId, null, "test",
            null, null, twoDaysFromNow, null, -0.9, null, null, Map.of()), TENANT);

        List<CuriositySignal> signals = generator.computeSignals(TENANT, Set.of());

        CuriositySignal proximitySignal = signals.stream()
            .filter(s -> s.category() == SignalCategory.PROXIMITY
                && s.description().contains("Funeral"))
            .findFirst().orElse(null);
        assertThat(proximitySignal).isNotNull();
        assertThat(proximitySignal.score()).isGreaterThan(0.5);
    }

    @Test
    void topicalDistanceDampeningReducesDistantSignals() {
        String sgId = store.createSubgraph(new SubgraphInput("People", SubgraphType.PERSON, null), TENANT);
        String aId = store.addNode(new NodeInput("A", sgId, null, "test",
            null, null, null, null, null, null, null, Map.of()), TENANT);
        String bId = store.addNode(new NodeInput("B", sgId, null, "test",
            null, null, null, null, null, null, null, Map.of()), TENANT);
        String cId = store.addNode(new NodeInput("C", sgId, null, "test",
            null, null, null, null, null, null, null, Map.of()), TENANT);
        String dId = store.addNode(new NodeInput("D", sgId, null, "test",
            null, null, null, null, null, null, null, Map.of()), TENANT);
        store.addEdge(new EdgeInput(aId, bId, "knows", null, "test",
            null, null, null, null, null, Map.of()), TENANT);
        store.addEdge(new EdgeInput(bId, cId, "knows", null, "test",
            null, null, null, null, null, Map.of()), TENANT);
        store.addEdge(new EdgeInput(cId, dId, "knows", null, "test",
            null, null, null, null, null, Map.of()), TENANT);

        List<CuriositySignal> signalsWithContext = generator.computeSignals(TENANT, Set.of(aId));
        List<CuriositySignal> signalsWithoutContext = generator.computeSignals(TENANT, Set.of());

        CuriositySignal dWithContext = signalsWithContext.stream()
            .filter(s -> s.targetNodeId() != null && s.targetNodeId().equals(dId))
            .findFirst().orElse(null);
        CuriositySignal dWithout = signalsWithoutContext.stream()
            .filter(s -> s.targetNodeId() != null && s.targetNodeId().equals(dId))
            .findFirst().orElse(null);

        if (dWithContext != null && dWithout != null) {
            assertThat(dWithContext.score()).isLessThan(dWithout.score());
        }
    }

    @Test
    void signalsSortedByScoreDescending() {
        String sgId = store.createSubgraph(new SubgraphInput("Mixed", SubgraphType.GENERAL, null), TENANT);
        store.addNode(new NodeInput("Orphan1", sgId, null, "test",
            null, null, null, null, null, null, null, Map.of()), TENANT);
        Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS);
        store.addNode(new NodeInput("Urgent Event", sgId, null, "test",
            null, null, tomorrow, null, null, null, null, Map.of()), TENANT);

        List<CuriositySignal> signals = generator.computeSignals(TENANT, Set.of());

        for (int i = 1; i < signals.size(); i++) {
            assertThat(signals.get(i).score())
                .isLessThanOrEqualTo(signals.get(i - 1).score());
        }
    }

    @Test
    void centralitySignalForHighDegreeNode() {
        String sgId = store.createSubgraph(new SubgraphInput("Network", SubgraphType.GENERAL, null), TENANT);
        String hubId = store.addNode(new NodeInput("Hub", sgId, null, "test",
            null, null, null, null, null, null, null, Map.of()), TENANT);
        for (int i = 0; i < 5; i++) {
            String leafId = store.addNode(new NodeInput("Leaf" + i, sgId, null, "test",
                null, null, null, null, null, null, null, Map.of()), TENANT);
            store.addEdge(new EdgeInput(hubId, leafId, "connects", null, "test",
                null, null, null, null, null, Map.of()), TENANT);
        }

        List<CuriositySignal> signals = generator.computeSignals(TENANT, Set.of());

        assertThat(signals).anyMatch(s ->
            s.category() == SignalCategory.CENTRALITY
            && s.question().contains("Hub"));
    }

    @Test
    void freshNodeIsNotStale() {
        String sgId = store.createSubgraph(new SubgraphInput("People", SubgraphType.PERSON, null), TENANT);
        store.addNode(new NodeInput("FreshNode", sgId, null, "test",
            null, null, null, null, null, null, null, Map.of()), TENANT);

        List<CuriositySignal> signals = generator.computeSignals(TENANT, Set.of());

        assertThat(signals).noneMatch(s ->
            s.category() == SignalCategory.TEMPORAL
            && s.description().contains("stale")
            && s.description().contains("FreshNode"));
    }
}
