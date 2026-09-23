package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GoalUrgencyTest {

    private InMemoryMindMapStore store;
    private String goalSubgraphId;
    private static final String TENANT = "t1";

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        goalSubgraphId = store.createSubgraph(
                new SubgraphInput("Goals", SubgraphTypes.GOAL, null), TENANT);
    }

    @Test
    void targetDatePresent_computesDynamicUrgency() {
        Instant now = Instant.parse("2026-09-23T12:00:00Z");
        String goalId = store.addNode(NodeInput.of("Goal", goalSubgraphId)
                .withProperties(Map.of(
                        "target-date", "2026-09-27",
                        "horizon", "medium")), TENANT);

        double urgency = GoalUrgency.computeUrgency(store.getNode(goalId, TENANT), now);

        // remaining ~3.5 days, budget 7 days → urgency ≈ 0.5
        assertThat(urgency).isCloseTo(0.5, within(0.05));
    }

    @Test
    void targetDateAbsent_fallsBackToStaticProperty() {
        Instant now = Instant.parse("2026-09-23T12:00:00Z");
        String goalId = store.addNode(NodeInput.of("Goal", goalSubgraphId)
                .withProperties(Map.of("urgency", "0.7")), TENANT);

        double urgency = GoalUrgency.computeUrgency(store.getNode(goalId, TENANT), now);

        assertThat(urgency).isEqualTo(0.7);
    }

    @Test
    void targetDatePastDue_returnsOne() {
        Instant now = Instant.parse("2026-09-25T12:00:00Z");
        String goalId = store.addNode(NodeInput.of("Goal", goalSubgraphId)
                .withProperties(Map.of(
                        "target-date", "2026-09-23",
                        "horizon", "medium")), TENANT);

        double urgency = GoalUrgency.computeUrgency(store.getNode(goalId, TENANT), now);

        assertThat(urgency).isEqualTo(1.0);
    }

    @Test
    void targetDateFarAway_clampedToZero() {
        Instant now = Instant.parse("2026-09-23T12:00:00Z");
        String goalId = store.addNode(NodeInput.of("Goal", goalSubgraphId)
                .withProperties(Map.of(
                        "target-date", "2026-10-23",
                        "horizon", "medium")), TENANT);

        double urgency = GoalUrgency.computeUrgency(store.getNode(goalId, TENANT), now);

        assertThat(urgency).isEqualTo(0.0);
    }

    @Test
    void differentHorizons_produceDifferentUrgency() {
        Instant now = Instant.parse("2026-09-23T12:00:00Z");
        // target-date 2.5 days away
        String shortGoal = store.addNode(NodeInput.of("Short", goalSubgraphId)
                .withProperties(Map.of(
                        "target-date", "2026-09-26",
                        "horizon", "short")), TENANT);

        String longGoal = store.addNode(NodeInput.of("Long", goalSubgraphId)
                .withProperties(Map.of(
                        "target-date", "2026-09-26",
                        "horizon", "long")), TENANT);

        double shortUrgency = GoalUrgency.computeUrgency(store.getNode(shortGoal, TENANT), now);
        double longUrgency = GoalUrgency.computeUrgency(store.getNode(longGoal, TENANT), now);

        // Short horizon (1 day): 2.5 days remaining > budget → clamped to 0
        assertThat(shortUrgency).isEqualTo(0.0);
        // Long horizon (30 days): 2.5 / 30 ≈ 0.083 → urgency ≈ 0.917
        assertThat(longUrgency).isCloseTo(0.917, within(0.02));
    }

    @Test
    void noTargetDateNoProperty_returnsZero() {
        Instant now = Instant.parse("2026-09-23T12:00:00Z");
        String goalId = store.addNode(NodeInput.of("Goal", goalSubgraphId)
                .withProperties(Map.of("description", "no urgency")), TENANT);

        double urgency = GoalUrgency.computeUrgency(store.getNode(goalId, TENANT), now);

        assertThat(urgency).isEqualTo(0.0);
    }

    @Test
    void isoDateTimeFormat_parsed() {
        Instant now = Instant.parse("2026-09-23T12:00:00Z");
        String goalId = store.addNode(NodeInput.of("Goal", goalSubgraphId)
                .withProperties(Map.of(
                        "target-date", "2026-09-30T12:00:00Z",
                        "horizon", "medium")), TENANT);

        double urgency = GoalUrgency.computeUrgency(store.getNode(goalId, TENANT), now);

        // exactly 7 days remaining = budget → urgency = 0.0
        assertThat(urgency).isCloseTo(0.0, within(0.01));
    }

    @Test
    void noHorizon_defaultsToMedium() {
        Instant now = Instant.parse("2026-09-23T12:00:00Z");
        String goalId = store.addNode(NodeInput.of("Goal", goalSubgraphId)
                .withProperties(Map.of("target-date", "2026-09-27")), TENANT);

        double urgency = GoalUrgency.computeUrgency(store.getNode(goalId, TENANT), now);

        // Same as medium: ~3.5 days remaining, 7 day budget → ~0.5
        assertThat(urgency).isCloseTo(0.5, within(0.05));
    }

    @Test
    void malformedTargetDate_fallsBackToStaticUrgency() {
        java.time.Instant now = java.time.Instant.parse("2026-09-23T12:00:00Z");
        String goalId = store.addNode(NodeInput.of("Bad date", goalSubgraphId)
                                               .withProperties(Map.of(
                                                       "target-date", "next-week",
                                                       "urgency", "0.6")), TENANT);

        double urgency = GoalUrgency.computeUrgency(store.getNode(goalId, TENANT), now);

        assertThat(urgency).isEqualTo(0.6);
    }

    @Test
    void malformedTargetDateNoFallback_returnsZero() {
        java.time.Instant now = java.time.Instant.parse("2026-09-23T12:00:00Z");
        String goalId = store.addNode(NodeInput.of("Bad date no fallback", goalSubgraphId)
                                               .withProperties(Map.of("target-date", "soon")), TENANT);

        double urgency = GoalUrgency.computeUrgency(store.getNode(goalId, TENANT), now);

        assertThat(urgency).isEqualTo(0.0);
    }
}
