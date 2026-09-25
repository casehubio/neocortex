package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.mindmap.AttentionSignal;
import io.casehub.neocortex.mindmap.CognitiveAttentionRequired;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SignalCategory;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.mindmap.runtime.IdleTracker;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AttentionIntegrationTest {

    private static final CurrentPrincipal PRINCIPAL = new CurrentPrincipal() {
        @Override public String actorId() { return "test"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return "t1"; }
        @Override public boolean isCrossTenantAdmin() { return true; }
    };

    @Test
    void full_pipeline_seed_goals_consolidate_fire_attention() {
        var store = new InMemoryMindMapStore();
        var memoryStore = new InMemoryMemoryStore(PRINCIPAL);
        memoryStore.store(MemoryInput.of(
            Subject.of("test", "seed"), new MemoryDomain("general"),
            "t1", "seed"));
        var fired = new ArrayList<CognitiveAttentionRequired>();
        var registry = CognitiveDefaultsRegistry.forTesting(
            CognitiveDefaults.empty("agent-1"));

        Clock clock = Clock.fixed(
            Instant.parse("2026-09-24T20:00:00Z"), ZoneOffset.UTC);

        var accumulator = new CognitiveAttentionAccumulator(
            registry, store, fired::add, clock, 0.5, 0);

        var goalSgId = store.createSubgraph(
            new SubgraphInput("Goals", SubgraphTypes.GOAL, null), "t1");
        store.addNode(NodeInput.of("Ship release", goalSgId)
            .withProperties(Map.of(
                "description", "Ship the release",
                "status", "active",
                "target-date", "2026-09-25",
                "horizon", "short",
                "agent-id", "agent-1",
                "feasibility", "0.5")), "t1");

        var priorityPhase = new GoalPrioritizationPhase(store, clock);
        var idleTracker = new IdleTracker();

        var scheduler = new ConsolidationScheduler(
            List.of(priorityPhase), idleTracker, memoryStore, null,
            e -> {}, 5, null, accumulator);

        scheduler.consolidateNow("t1");

        assertThat(fired).isNotEmpty();
        var briefing = fired.get(0).briefing();
        assertThat(briefing.principalId()).isEqualTo("agent-1");
        assertThat(briefing.signals()).anyMatch(
            s -> s.category() == SignalCategory.URGENCY_SPIKE);
    }

    @Test
    void full_pipeline_decay_creates_attention_signal() {
        var store = new InMemoryMindMapStore();
        var memoryStore = new InMemoryMemoryStore(PRINCIPAL);
        memoryStore.store(MemoryInput.of(
            Subject.of("test", "seed"), new MemoryDomain("general"),
            "t1", "seed"));
        var fired = new ArrayList<CognitiveAttentionRequired>();
        var registry = CognitiveDefaultsRegistry.forTesting(
            CognitiveDefaults.empty("agent-2"));
        var accumulator = new CognitiveAttentionAccumulator(
            registry, store, fired::add, Clock.systemUTC(), 0.5, 0);

        var goalSgId = store.createSubgraph(
            new SubgraphInput("Goals", SubgraphTypes.GOAL, null), "t1");
        store.addNode(NodeInput.of("Stale goal", goalSgId)
            .withConfidence(io.casehub.neocortex.cognitive.Confidence.stated(
                0.3, Instant.parse("2026-08-01T00:00:00Z")))
            .withProperties(Map.of(
                "description", "Neglected goal",
                "status", "active",
                "agent-id", "agent-2"))
            .withPleasure(0.0), "t1");

        var priorityPhase = new GoalPrioritizationPhase(store);
        var idleTracker = new IdleTracker();
        var scheduler = new ConsolidationScheduler(
            List.of(priorityPhase), idleTracker, memoryStore, null,
            e -> {}, 5, null, accumulator);

        scheduler.consolidateNow("t1");

        assertThat(fired).isNotEmpty();
        assertThat(fired.get(0).briefing().principalId()).isEqualTo("agent-2");
        assertThat(fired.get(0).briefing().signals()).anyMatch(
            s -> s.category() == SignalCategory.DECAY_DETECTED);
    }

    @Test
    void real_time_affect_plus_consolidation_signals_combine() {
        var store = new InMemoryMindMapStore();
        var memoryStore = new InMemoryMemoryStore(PRINCIPAL);
        memoryStore.store(MemoryInput.of(
            Subject.of("test", "seed"), new MemoryDomain("general"),
            "t1", "seed"));
        var fired = new ArrayList<CognitiveAttentionRequired>();
        var registry = CognitiveDefaultsRegistry.forTesting(
            CognitiveDefaults.empty("agent-1"));
        var accumulator = new CognitiveAttentionAccumulator(
            registry, store, fired::add, Clock.systemUTC(), 3.0, 0);

        // Create a node with initial PAD
        var sg = store.createSubgraph(
            new SubgraphInput("People", "person", null), "t1");
        var nodeId = store.addNode(NodeInput.of("Agent entity", sg)
            .withProperty("agent-id", "agent-1")
            .withPleasure(0.8).withArousal(0.2).withDominance(0.5), "t1");

        // Prime the PAD cache
        accumulator.onAffectRecorded(
            new io.casehub.neocortex.memory.mood.AffectRecorded(nodeId, "t1", "m1"));
        assertThat(fired).isEmpty();

        // Significant PAD change — creates signal (delta > 0.3) but below threshold 3.0
        store.updateNode(nodeId, io.casehub.neocortex.mindmap.NodeUpdate.empty()
            .withPad(-0.5, 0.9, 0.1), "t1");
        accumulator.onAffectRecorded(
            new io.casehub.neocortex.memory.mood.AffectRecorded(nodeId, "t1", "m2"));

        // The affect signal's significance (~1.56 Euclidean dist) is below 3.0 threshold
        // Add a consolidation signal to push over threshold
        accumulator.addSignals(List.of(new AttentionSignal(
            "agent-1", "t1", SignalCategory.URGENCY_SPIKE,
            "goal-1", "Ship it", 2.0, "urgency")));

        assertThat(fired).hasSize(1);
        var signals = fired.get(0).briefing().signals();
        assertThat(signals).hasSize(2);
        assertThat(signals).anyMatch(s -> s.category() == SignalCategory.AFFECT_CHANGE);
        assertThat(signals).anyMatch(s -> s.category() == SignalCategory.URGENCY_SPIKE);
    }
}
