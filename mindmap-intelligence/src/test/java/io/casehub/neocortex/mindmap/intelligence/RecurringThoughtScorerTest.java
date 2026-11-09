package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.RecurringThoughtConfig;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.neocortex.mindmap.ContinuumPosition;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringThoughtScorerTest {

    private InMemoryMindMapStore mindMapStore;
    private InMemoryMemoryStore memoryStore;
    private RecurringThoughtScorer scorer;
    private static final String TENANT = "test-tenant";
    private static final Instant NOW = Instant.now();

    private final CurrentPrincipal principal = new CurrentPrincipal() {
        @Override public String actorId() { return "actor"; }
        @Override public java.util.Set<String> groups() { return java.util.Set.of(); }
        @Override public String tenancyId() { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return true; }
    };

    @BeforeEach
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        memoryStore = new InMemoryMemoryStore(principal);
        scorer = new RecurringThoughtScorer(mindMapStore, memoryStore);
    }

    @Test
    void noDataReturnsHealthy() {
        String sgId = createSubgraph();
        String nodeId = mindMapStore.addNode(NodeInput.of("topic", sgId)
                .withConfidence(Confidence.stated(0.5, NOW)), TENANT);

        var assessment = scorer.score(nodeId, TENANT,
                RecurringThoughtConfig.defaults(), null, null);
        assertThat(assessment.position()).isEqualTo(ContinuumPosition.HEALTHY);
        assertThat(assessment.compositeScore()).isEqualTo(0.0);
    }

    @Test
    void belowMinObservationsAlwaysHealthy() {
        String sgId = createSubgraph();
        String nodeId = mindMapStore.addNode(NodeInput.of("dark-topic", sgId)
                .withConfidence(Confidence.stated(0.2, NOW))
                .withPleasure(-0.9).withArousal(0.9), TENANT);
        addExperienceMemories("dark-topic", 2);

        var assessment = scorer.score(nodeId, TENANT,
                RecurringThoughtConfig.defaults(), null, null);
        assertThat(assessment.position()).isEqualTo(ContinuumPosition.HEALTHY);
    }

    @Test
    void highRecurrenceAndNegativeValenceProducesNonHealthy() {
        String sgId = createSubgraph();
        String nodeId = mindMapStore.addNode(NodeInput.of("obsession-topic", sgId)
                .withConfidence(Confidence.stated(0.3, NOW))
                .withPleasure(-0.7).withArousal(0.8), TENANT);
        addExperienceMemories("obsession-topic", 8);

        var assessment = scorer.score(nodeId, TENANT,
                RecurringThoughtConfig.defaults(), null, null);
        assertThat(assessment.position()).isNotEqualTo(ContinuumPosition.HEALTHY);
        assertThat(assessment.compositeScore()).isGreaterThan(0.3);
        assertThat(assessment.thoughtRecurrence()).isGreaterThan(0.0);
        assertThat(assessment.valence()).isGreaterThan(0.0);
    }

    @Test
    void customWeightsOnlyValenceAffectsScore() {
        String sgId = createSubgraph();
        String nodeId = mindMapStore.addNode(NodeInput.of("val-topic", sgId)
                .withConfidence(Confidence.stated(0.8, NOW))
                .withPleasure(-0.9), TENANT);
        addExperienceMemories("val-topic", 3);

        var config = new RecurringThoughtConfig(
                Map.of("valence", 1.0, "thoughtRecurrence", 0.0, "habituationFailure", 0.0,
                        "gapClosureResistance", 0.0, "attentionalCapture", 0.0,
                        "arousalTrajectory", 0.0, "actionTendency", 0.0),
                0.3, 0.6, 0.8, Duration.ofDays(7), 1);

        var assessment = scorer.score(nodeId, TENANT, config, null, null);
        assertThat(assessment.compositeScore()).isCloseTo(assessment.valence(), org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void classifyMapsCorrectly() {
        var config = RecurringThoughtConfig.defaults();
        assertThat(RecurringThoughtScorer.classify(0.0, config)).isEqualTo(ContinuumPosition.HEALTHY);
        assertThat(RecurringThoughtScorer.classify(0.29, config)).isEqualTo(ContinuumPosition.HEALTHY);
        assertThat(RecurringThoughtScorer.classify(0.3, config)).isEqualTo(ContinuumPosition.FIXATION);
        assertThat(RecurringThoughtScorer.classify(0.59, config)).isEqualTo(ContinuumPosition.FIXATION);
        assertThat(RecurringThoughtScorer.classify(0.6, config)).isEqualTo(ContinuumPosition.OBSESSION);
        assertThat(RecurringThoughtScorer.classify(0.79, config)).isEqualTo(ContinuumPosition.OBSESSION);
        assertThat(RecurringThoughtScorer.classify(0.8, config)).isEqualTo(ContinuumPosition.RUMINATION);
        assertThat(RecurringThoughtScorer.classify(1.0, config)).isEqualTo(ContinuumPosition.RUMINATION);
    }

    @Test
    void gapClosureResistance_lowConfidenceScoresHigh() {
        assertThat(scorer.scoreGapClosureResistance(null)).isEqualTo(0.0);
    }

    @Test
    void arousalTrajectory_negativeArousalAndPleasureSignalsRumination() {
        String sgId = createSubgraph();
        String nodeId = mindMapStore.addNode(NodeInput.of("despair", sgId)
                .withPleasure(-0.5).withArousal(-0.5), TENANT);
        var node = mindMapStore.getNode(nodeId, TENANT);
        assertThat(scorer.scoreArousalTrajectory(node)).isEqualTo(0.9);
    }

    @Test
    void valence_negativePleasureScoresHigh() {
        String sgId = createSubgraph();
        String nodeId = mindMapStore.addNode(NodeInput.of("pain", sgId)
                .withPleasure(-0.8), TENANT);
        var node = mindMapStore.getNode(nodeId, TENANT);
        assertThat(scorer.scoreValence(node)).isCloseTo(0.8, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void nonexistentNodeReturnsHealthy() {
        var assessment = scorer.score("nonexistent", TENANT,
                RecurringThoughtConfig.defaults(), null, null);
        assertThat(assessment.position()).isEqualTo(ContinuumPosition.HEALTHY);
    }

    private String createSubgraph() {
        return mindMapStore.createSubgraph(
                new SubgraphInput("cognitive", SubgraphTypes.CONCEPT, null), TENANT);
    }

    private void addExperienceMemories(String topicName, int count) {
        for (int i = 0; i < count; i++) {
            memoryStore.store(MemoryInput.of(
                    Subject.of("agent", "agent-1"),
                    ExperienceEvents.DOMAIN,
                    TENANT,
                    "Observation about " + topicName + " iteration " + i)
                    .withAttributes(Map.of("event-type", "observation", "subject", topicName)));
        }
    }
}
