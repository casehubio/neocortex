package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.neocortex.mindmap.AppraisalContext;
import io.casehub.neocortex.mindmap.GoalAppraisal;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.mindmap.intelligence.HeuristicGoalAppraisal;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end: neocortex processes surfacing events and produces the correct
 * emotional progression — Day 1 to Day 7 — without any LLM or blocks involvement.
 *
 * Simulates:
 *   Day 1:  Goal created with 7-day deadline. First surfacing. Hope dominant.
 *   Day 3:  Surfaced 3 times, no progress. Fear rising, Hope declining.
 *   Day 6:  Surfaced 6 times, no progress. Fear dominant, high intensity.
 *   Day 7:  Deadline day. Fears-confirmed or Relief depending on progress.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GoalEmotionalProgressionTest {

    private InMemoryMindMapStore mindMapStore;
    private InMemoryMemoryStore memoryStore;
    private GoalAppraisal appraisal;
    private SurfacingAggregationPhase surfacingPhase;
    private String goalSubgraphId;
    private String birthdayGoalId;

    private static final String TENANT = "progression-test";
    private static final Instant DAY_1 = Instant.parse("2026-09-17T10:00:00Z");
    private static final MemoryDomain EXPERIENCE = new MemoryDomain("experience");

    @BeforeAll
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        CurrentPrincipal principal = new CurrentPrincipal() {
            @Override public String actorId() { return "test-agent"; }
            @Override public Set<String> groups() { return Set.of(); }
            @Override public String tenancyId() { return TENANT; }
            @Override public boolean isCrossTenantAdmin() { return true; }
        };
        memoryStore = new InMemoryMemoryStore(principal);
        appraisal = new HeuristicGoalAppraisal();
        surfacingPhase = new SurfacingAggregationPhase(mindMapStore, memoryStore);
        goalSubgraphId = mindMapStore.createSubgraph(
                new SubgraphInput("Goals", SubgraphTypes.GOAL, null), TENANT);
    }

    @Test @Order(1)
    void createGoalWithDeadline() {
        birthdayGoalId = mindMapStore.addNode(
                NodeInput.of("Buy daughter's birthday gift", goalSubgraphId)
                        .withProperties(Map.of(
                                "description", "Buy a birthday gift for daughter",
                                "status", "active",
                                "priority", "0.85",
                                "urgency", "0.2",
                                "feasibility", "0.8",
                                "target-date", "2026-09-24T10:00:00Z",
                                "horizon", "short",
                                "affected-entity", "daughter")),
                TENANT);

        assertThat(birthdayGoalId).isNotNull();
        var node = mindMapStore.getNode(birthdayGoalId, TENANT);
        assertThat(node.name()).isEqualTo("Buy daughter's birthday gift");
    }

    @Test @Order(2)
    void day1_firstSurfacing_hopeDominant() {
        recordSurfacing(birthdayGoalId);
        surfacingPhase.run(TENANT, List.of());

        var node = mindMapStore.getNode(birthdayGoalId, TENANT);
        assertThat(node.property("surfaced-count")).hasValue("1");
        assertThat(node.property("first-surfaced-at")).isPresent();

        var emotions = appraisal.appraise(node, appraisalCtx(node));
        assertThat(emotions).anyMatch(e -> e.type() == EmotionType.HOPE);

        var hope = emotions.stream()
                .filter(e -> e.type() == EmotionType.HOPE).findFirst().orElseThrow();
        var fear = emotions.stream()
                .filter(e -> e.type() == EmotionType.FEAR).findFirst().orElse(null);

        assertThat(hope.intensity()).isGreaterThan(0.3);
        if (fear != null) {
            assertThat(hope.intensity()).isGreaterThan(fear.intensity());
        }
    }

    @Test @Order(3)
    void day3_noProgress_fearRising() {
        recordSurfacing(birthdayGoalId);
        recordSurfacing(birthdayGoalId);
        surfacingPhase.run(TENANT, List.of());

        var node = mindMapStore.getNode(birthdayGoalId, TENANT);
        assertThat(node.property("surfaced-count")).hasValue("3");
        assertThat(node.property("surfacing-progress-gap")).hasValue("3");

        updateNodeUrgency(birthdayGoalId, "0.5");
        node = mindMapStore.getNode(birthdayGoalId, TENANT);

        var emotions = appraisal.appraise(node, appraisalCtx(node));
        var fear = emotions.stream()
                .filter(e -> e.type() == EmotionType.FEAR).findFirst().orElse(null);

        assertThat(fear).isNotNull();
        assertThat(fear.intensity()).isGreaterThan(0.2);
    }

    @Test @Order(4)
    void day6_repeatedSurfacing_fearDominant() {
        recordSurfacing(birthdayGoalId);
        recordSurfacing(birthdayGoalId);
        recordSurfacing(birthdayGoalId);
        surfacingPhase.run(TENANT, List.of());

        var node = mindMapStore.getNode(birthdayGoalId, TENANT);
        assertThat(node.property("surfaced-count")).hasValue("6");
        assertThat(node.property("surfacing-progress-gap")).hasValue("6");

        updateNodeUrgency(birthdayGoalId, "0.9");
        node = mindMapStore.getNode(birthdayGoalId, TENANT);

        var emotions = appraisal.appraise(node, appraisalCtx(node));
        var fear = emotions.stream()
                .filter(e -> e.type() == EmotionType.FEAR).findFirst().orElseThrow();

        assertThat(fear.intensity()).isGreaterThan(0.5);
        assertThat(fear.pad().pleasure()).isLessThan(0);
        assertThat(fear.pad().arousal()).isGreaterThan(0);
    }

    @Test @Order(5)
    void day6_applyOccAffect_padReflectsWorry() {
        var node = mindMapStore.getNode(birthdayGoalId, TENANT);
        var affectPhase = new GoalAffectPhase(mindMapStore, appraisal,
                Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC));
        affectPhase.run(TENANT, List.of());

        node = mindMapStore.getNode(birthdayGoalId, TENANT);
        assertThat(node.pleasure()).isNotNull();
        assertThat(node.pleasure()).isLessThan(0);
        assertThat(node.arousal()).isNotNull();
        assertThat(node.arousal()).isGreaterThan(0);
    }

    @Test @Order(6)
    void progressResets_fearDeclines() {
        recordProgress(birthdayGoalId);
        surfacingPhase.run(TENANT, List.of());

        var node = mindMapStore.getNode(birthdayGoalId, TENANT);
        assertThat(node.property("last-progress-at")).isPresent();

        int gap = Integer.parseInt(node.property("surfacing-progress-gap").orElse("999"));
        assertThat(gap).isLessThan(6);

        var emotions = appraisal.appraise(node, appraisalCtx(node));
        var fear = emotions.stream()
                .filter(e -> e.type() == EmotionType.FEAR).findFirst().orElse(null);

        double previousFearIntensity = 0.5;
        if (fear != null) {
            assertThat(fear.intensity()).isLessThan(previousFearIntensity);
        }
    }

    @Test @Order(7)
    void completedGoal_satisfactionEmerges() {
        mindMapStore.updateNode(birthdayGoalId,
                io.casehub.neocortex.mindmap.NodeUpdate.empty()
                        .withPropertiesToSet(Map.of("status", "completed")),
                TENANT);

        var node = mindMapStore.getNode(birthdayGoalId, TENANT);
        var emotions = appraisal.appraise(node, appraisalCtx(node));

        assertThat(emotions).anyMatch(e -> e.type() == EmotionType.SATISFACTION);
        assertThat(emotions).noneMatch(e -> e.type() == EmotionType.FEAR);
    }

    private AppraisalContext appraisalCtx(MindMapNode node) {
        int surfacingCount = node.property("surfacing-progress-gap")
                .or(() -> node.property("surfaced-count"))
                .map(Integer::parseInt).orElse(0);
        String lastProgressStr = node.property("last-progress-at").orElse(null);
        Instant lastProgress = lastProgressStr != null ? Instant.parse(lastProgressStr) : null;
        String lastSurfacedStr = node.property("last-surfaced-at").orElse(null);
        Instant lastSurfaced = lastSurfacedStr != null ? Instant.parse(lastSurfacedStr) : null;

        return new AppraisalContext(TENANT, "test-agent",
                io.casehub.neocortex.cognitive.PadProjection.NEUTRAL,
                surfacingCount, lastProgress, lastSurfaced, Map.of());
    }

    private void recordSurfacing(String goalNodeId) {
        memoryStore.store(MemoryInput.of(Subject.of("agent", "test-agent"), EXPERIENCE, TENANT,
                "Goal surfaced: " + goalNodeId)
                .withAttributes(Map.of(
                        "cognitive-event", "goal-surfaced",
                        "goal-node-id", goalNodeId)));
    }

    private void recordProgress(String goalNodeId) {
        memoryStore.store(MemoryInput.of(Subject.of("agent", "test-agent"), EXPERIENCE, TENANT,
                "Progress on: " + goalNodeId)
                .withAttributes(Map.of(
                        "cognitive-event", "goal-progress",
                        "goal-node-id", goalNodeId)));
    }

    private void updateNodeUrgency(String nodeId, String urgency) {
        mindMapStore.updateNode(nodeId,
                io.casehub.neocortex.mindmap.NodeUpdate.empty()
                        .withPropertiesToSet(Map.of("urgency", urgency)),
                TENANT);
    }
}
