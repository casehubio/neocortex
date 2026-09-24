package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.EmotionSource;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.PadProjection;
import io.casehub.neocortex.mindmap.AppraisalContext;
import io.casehub.neocortex.mindmap.GoalAppraisal;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicGoalAppraisalTest {

    private              GoalAppraisal        appraisal;
    private              InMemoryMindMapStore store;
    private              String               goalSubgraphId;
    private static final String               TENANT = "t1";

    @BeforeEach
    void setUp() {
        appraisal      = new HeuristicGoalAppraisal();
        store          = new InMemoryMindMapStore();
        goalSubgraphId = store.createSubgraph(
                new SubgraphInput("Goals", SubgraphTypes.GOAL, null), TENANT);
    }

    @Test
    void activeGoalLowUrgency_producesHope() {
        var node     = createGoal("active", 0.7, 0.2, 0.8);
        var emotions = appraisal.appraise(node, ctx(0, null));
        assertThat(emotions).anyMatch(e -> e.type() == EmotionType.HOPE);
        assertThat(emotions).noneMatch(e -> e.type() == EmotionType.FEAR);
    }

    @Test
    void activeGoalHighUrgencyNoProgress_producesFear() {
        var node     = createGoal("active", 0.85, 0.9, 0.4);
        var emotions = appraisal.appraise(node, ctx(5, null));
        assertThat(emotions).anyMatch(e -> e.type() == EmotionType.FEAR && e.intensity() > 0.5);
    }

    @Test
    void completedGoal_producesSatisfaction() {
        var node     = createGoal("completed", 0.7, 0.0, 1.0);
        var emotions = appraisal.appraise(node, ctx(0, null));
        assertThat(emotions).anyMatch(e -> e.type() == EmotionType.SATISFACTION);
    }

    @Test
    void blockedGoal_producesDistress() {
        var node     = createGoal("blocked", 0.8, 0.7, 0.1);
        var emotions = appraisal.appraise(node, ctx(0, null));
        assertThat(emotions).anyMatch(e -> e.type() == EmotionType.DISTRESS);
    }

    @Test
    void abandonedGoal_producesDisappointment() {
        var node     = createGoal("abandoned", 0.6, 0.0, 0.0);
        var emotions = appraisal.appraise(node, ctx(0, null));
        assertThat(emotions).anyMatch(e -> e.type() == EmotionType.DISAPPOINTMENT);
    }

    @Test
    void surfacingGapAmplifiesFear() {
        var node = createGoal("active", 0.85, 0.8, 0.4);

        var emotions3 = appraisal.appraise(node, ctx(3, null));
        var emotions7 = appraisal.appraise(node, ctx(7, null));

        double fear3 = emotions3.stream()
                                .filter(e -> e.type() == EmotionType.FEAR).findFirst()
                                .map(CognitiveEmotion::intensity).orElse(0.0);
        double fear7 = emotions7.stream()
                                .filter(e -> e.type() == EmotionType.FEAR).findFirst()
                                .map(CognitiveEmotion::intensity).orElse(0.0);

        assertThat(fear7).isGreaterThan(fear3);
    }

    @Test
    void surfacingGapSaturates() {
        var node = createGoal("active", 0.85, 0.8, 0.4);

        var emotions50  = appraisal.appraise(node, ctx(50, null));
        var emotions100 = appraisal.appraise(node, ctx(100, null));

        double fear50 = emotions50.stream()
                                  .filter(e -> e.type() == EmotionType.FEAR).findFirst()
                                  .map(CognitiveEmotion::intensity).orElse(0.0);
        double fear100 = emotions100.stream()
                                    .filter(e -> e.type() == EmotionType.FEAR).findFirst()
                                    .map(CognitiveEmotion::intensity).orElse(0.0);

        assertThat(fear100 - fear50).isLessThan(0.05);
    }

    @Test
    void empathicPity_whenRelationshipPresent() {
        var node = createGoalWithAffectedEntity("active", 0.85, 0.9, 0.3, "daughter-node");
        var ctx = new AppraisalContext("t1", "agent-1",
                                       PadProjection.NEUTRAL, 5, null, null,
                                       Map.of("daughter-node", 0.8));
        var emotions = appraisal.appraise(node, ctx);
        assertThat(emotions).anyMatch(e ->
                                              e.type() == EmotionType.PITY && e.source() == EmotionSource.EMPATHIC);
    }

    @Test
    void noEmpathicPity_whenNoRelationship() {
        var node     = createGoalWithAffectedEntity("active", 0.85, 0.9, 0.3, "unknown-node");
        var emotions = appraisal.appraise(node, ctx(0, null));
        assertThat(emotions).noneMatch(e -> e.source() == EmotionSource.EMPATHIC);
    }

    @Test
    void allEmotionsHaveSourceAndPad() {
        var node     = createGoal("active", 0.7, 0.7, 0.5);
        var emotions = appraisal.appraise(node, ctx(3, null));
        assertThat(emotions).allMatch(e -> e.source() != null);
        assertThat(emotions).allMatch(e -> e.pad() != null);
    }

    @Test
    void hopeAndFearCoexistAtMidUrgency() {
        var node     = createGoal("active", 0.7, 0.5, 0.6);
        var emotions = appraisal.appraise(node, ctx(2, null));
        assertThat(emotions).anyMatch(e -> e.type() == EmotionType.HOPE);
        assertThat(emotions).anyMatch(e -> e.type() == EmotionType.FEAR);
    }

    @Test
    void dormantGoal_producesLowIntensity() {
        var node     = createGoal("dormant", 0.3, 0.0, 0.0);
        var emotions = appraisal.appraise(node, ctx(0, null));
        assertThat(emotions).allMatch(e -> e.intensity() < 0.3);
    }

    private AppraisalContext ctx(int surfacingCount, Instant lastProgressAt) {
        return new AppraisalContext(TENANT, "agent-1",
                                    PadProjection.NEUTRAL, surfacingCount, lastProgressAt, null, Map.of());
    }

    private MindMapNode createGoal(String status, double priority,
                                   double urgency, double feasibility) {
        String id = store.addNode(NodeInput.of("Goal-" + status, goalSubgraphId)
                                           .withProperties(Map.of(
                                                   "description", "test goal",
                                                   "status", status,
                                                   "priority", String.valueOf(priority),
                                                   "urgency", String.valueOf(urgency),
                                                   "feasibility", String.valueOf(feasibility))), TENANT);
        return store.getNode(id, TENANT);
    }

    private MindMapNode createGoalWithAffectedEntity(String status, double priority,
                                                     double urgency, double feasibility,
                                                     String affectedEntity) {
        String id = store.addNode(NodeInput.of("Goal-" + status, goalSubgraphId)
                                           .withProperties(Map.of(
                                                   "description", "test goal",
                                                   "status", status,
                                                   "priority", String.valueOf(priority),
                                                   "urgency", String.valueOf(urgency),
                                                   "feasibility", String.valueOf(feasibility),
                                                   "affected-entity", affectedEntity)), TENANT);
        return store.getNode(id, TENANT);
    }
}
