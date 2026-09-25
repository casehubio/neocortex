package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.neocortex.mindmap.runtime.IdleTracker;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConsolidationSchedulerTest {

    private static final CurrentPrincipal PRINCIPAL = new CurrentPrincipal() {
        @Override public String actorId() { return "test-actor"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return "tenant-1"; }
        @Override public boolean isCrossTenantAdmin() { return true; }
    };

    private IdleTracker idleTracker;
    private CaseMemoryStore memoryStore;
    private List<String> executedPhases;
    private ConsolidationScheduler scheduler;

    @BeforeEach
    void setUp() {
        idleTracker = new IdleTracker();
        memoryStore = new InMemoryMemoryStore(PRINCIPAL);
        memoryStore.store(MemoryInput.of(
            Subject.of("test", "seed"), new MemoryDomain("general"),
            "tenant-1", "seed entry"));

        executedPhases = new ArrayList<>();
        ConsolidationPhase phase1 = new ConsolidationPhase() {
            @Override public String name() { return "phase-1"; }
            @Override public void run(String tenantId, List<String> sp) {
                executedPhases.add(name() + ":" + tenantId);
            }
        };
        ConsolidationPhase phase2 = new ConsolidationPhase() {
            @Override public String name() { return "phase-2"; }
            @Override public void run(String tenantId, List<String> sp) {
                executedPhases.add(name() + ":" + tenantId);
            }
        };

        scheduler = new ConsolidationScheduler(
            List.of(phase1, phase2), idleTracker, memoryStore, null);
    }

    @Test
    void tick_whenIdle_executesAllPhases() {
        scheduler.tick();
        assertThat(executedPhases).containsExactly(
            "phase-1:tenant-1", "phase-2:tenant-1");
    }

    @Test
    void tick_whenNotIdle_skips() {
        idleTracker.recordWrite();
        scheduler.tick();
        assertThat(executedPhases).isEmpty();
    }

    @Test
    void tick_phaseFailure_continuesNextPhase() {
        ConsolidationPhase failing = new ConsolidationPhase() {
            @Override public String name() { return "failing"; }
            @Override public void run(String tenantId, List<String> sp) {
                throw new RuntimeException("boom");
            }
        };
        ConsolidationPhase healthy = new ConsolidationPhase() {
            @Override public String name() { return "healthy"; }
            @Override public void run(String tenantId, List<String> sp) {
                executedPhases.add("healthy:" + tenantId);
            }
        };

        var schedulerWithFailure = new ConsolidationScheduler(
            List.of(failing, healthy), idleTracker, memoryStore, null);
        schedulerWithFailure.tick();

        assertThat(executedPhases).containsExactly("healthy:tenant-1");
    }

    @Test
    void consolidateNow_runsAllPhasesForTenant() {
        scheduler.consolidateNow("tenant-1");
        assertThat(executedPhases).containsExactly(
                "phase-1:tenant-1", "phase-2:tenant-1");
    }

    @Test
    void consolidateNow_bypassesIdleCheck() {
        idleTracker.recordWrite();
        scheduler.consolidateNow("tenant-1");
        assertThat(executedPhases).containsExactly(
                "phase-1:tenant-1", "phase-2:tenant-1");
    }


    @Test
    void tick_resetsSignificanceAccumulator() {
        var accumulator = new SignificanceAccumulator(
                e -> 1.0, 100.0, t -> {});

        var schedulerWithAccumulator = new ConsolidationScheduler(
                List.of(), idleTracker, memoryStore, null, e -> {},
                5, accumulator, null);

        var event = new io.casehub.neocortex.memory.experience.ExperienceRecorded(
                new io.casehub.neocortex.memory.experience.Observation("a1", "tenant-1", null, "t1",
                                                                       java.time.Instant.now(), "test event", null, java.util.Map.of(), "entity-1"),
                "m1");
        accumulator.onExperienceRecorded(event);

        schedulerWithAccumulator.tick();

        SignificanceSnapshot snapshot = accumulator.swapAndReset();
        assertThat(snapshot.perTenant()).isEmpty();
    }

    @Test
    void consolidateNow_resetsSignificanceAccumulator() {
        var accumulator = new SignificanceAccumulator(
                e -> 1.0, 100.0, t -> {});

        var schedulerWithAccumulator = new ConsolidationScheduler(
                List.of(), idleTracker, memoryStore, null, e -> {},
                5, accumulator, null);

        var event = new io.casehub.neocortex.memory.experience.ExperienceRecorded(
                new io.casehub.neocortex.memory.experience.Observation("a1", "tenant-1", null, "t1",
                                                                       java.time.Instant.now(), "test event", null, java.util.Map.of(), "entity-1"),
                "m1");
        accumulator.onExperienceRecorded(event);

        schedulerWithAccumulator.consolidateNow("tenant-1");

        SignificanceSnapshot snapshot = accumulator.swapAndReset();
        assertThat(snapshot.perTenant()).isEmpty();
    }


    @Test
    void runPhases_collects_signals_and_feeds_accumulator() {
        var firedEvents = new java.util.ArrayList<io.casehub.neocortex.mindmap.CognitiveAttentionRequired>();
        var registry = io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry.forTesting(
                io.casehub.neocortex.cognitive.index.CognitiveDefaults.empty("agent-a"));
        var accumulator = new CognitiveAttentionAccumulator(
                registry, null, firedEvents::add, java.time.Clock.systemUTC(), 3.0, 0);

        var signalPhase = new ConsolidationPhase() {
            private final java.util.List<io.casehub.neocortex.mindmap.AttentionSignal> pending = new java.util.ArrayList<>();

            @Override
            public String name()    {return "signal-phase";}

            @Override
            public void beginTick() {pending.clear();}

            @Override
            public void run(String tenantId, java.util.List<String> sp) {
                pending.add(new io.casehub.neocortex.mindmap.AttentionSignal(
                        "agent-a", tenantId, io.casehub.neocortex.mindmap.SignalCategory.URGENCY_SPIKE,
                        "n1", "Urgent goal", 4.0, "urgency 0.9"));
            }

            @Override
            public java.util.List<io.casehub.neocortex.mindmap.AttentionSignal> signals() {
                var result = java.util.List.copyOf(pending);
                pending.clear();
                return result;
            }
        };

        var schedulerWithAccumulator = new ConsolidationScheduler(
                java.util.List.of(signalPhase), idleTracker, memoryStore, null,
                e -> {}, 5, null, accumulator);

        schedulerWithAccumulator.consolidateNow("tenant-1");

        assertThat(firedEvents).hasSize(1);
        assertThat(firedEvents.get(0).briefing().principalId()).isEqualTo("agent-a");
        assertThat(firedEvents.get(0).briefing().signals()).hasSize(1);
        assertThat(firedEvents.get(0).briefing().signals().get(0).category())
                .isEqualTo(io.casehub.neocortex.mindmap.SignalCategory.URGENCY_SPIKE);
    }

    @Test
    void runPhases_without_accumulator_does_not_fail() {
        var signalPhase = new ConsolidationPhase() {
            @Override
            public String name()                                        {return "signal-phase";}

            @Override
            public void run(String tenantId, java.util.List<String> sp) {}

            @Override
            public java.util.List<io.casehub.neocortex.mindmap.AttentionSignal> signals() {
                return java.util.List.of(new io.casehub.neocortex.mindmap.AttentionSignal(
                        "a", "t", io.casehub.neocortex.mindmap.SignalCategory.URGENCY_SPIKE,
                        "n1", "G1", 5.0, "r"));
            }
        };

        var schedulerNoAccumulator = new ConsolidationScheduler(
                java.util.List.of(signalPhase), idleTracker, memoryStore, null);

        schedulerNoAccumulator.consolidateNow("tenant-1");
        // No exception thrown — accumulator is null, signals collected but discarded
    }


    @Test
    void runPhases_updates_urgency_p75_on_accumulator() {
        var firedEvents = new java.util.ArrayList<io.casehub.neocortex.mindmap.CognitiveAttentionRequired>();
        var registry = io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry.forTesting(
                io.casehub.neocortex.cognitive.index.CognitiveDefaults.empty("agent-a"));
        var accumulator = new CognitiveAttentionAccumulator(
                registry, null, firedEvents::add, java.time.Clock.systemUTC(), 10.0, 0);

        var phase = new ConsolidationPhase() {
            @Override
            public String name()                                        {return "urgency-phase";}

            @Override
            public void run(String tenantId, java.util.List<String> sp) {}

            @Override
            public java.util.List<io.casehub.neocortex.mindmap.AttentionSignal> signals() {
                return java.util.List.of(
                        new io.casehub.neocortex.mindmap.AttentionSignal(
                                "agent-a", "t1", io.casehub.neocortex.mindmap.SignalCategory.URGENCY_SPIKE,
                                "n1", "Goal 1", 0.9, "high urgency"),
                        new io.casehub.neocortex.mindmap.AttentionSignal(
                                "agent-a", "t1", io.casehub.neocortex.mindmap.SignalCategory.URGENCY_SPIKE,
                                "n2", "Goal 2", 0.5, "medium urgency"),
                        new io.casehub.neocortex.mindmap.AttentionSignal(
                                "agent-a", "t1", io.casehub.neocortex.mindmap.SignalCategory.URGENCY_SPIKE,
                                "n3", "Goal 3", 0.8, "high urgency"),
                        new io.casehub.neocortex.mindmap.AttentionSignal(
                                "agent-a", "t1", io.casehub.neocortex.mindmap.SignalCategory.URGENCY_SPIKE,
                                "n4", "Goal 4", 0.3, "low urgency")
                                        );
            }
        };

        var schedulerWithAccumulator = new ConsolidationScheduler(
                java.util.List.of(phase), idleTracker, memoryStore, null,
                e -> {}, 5, null, accumulator);

        schedulerWithAccumulator.consolidateNow("t1");

        // After consolidation, urgencyP75 should be computed and set.
        // With values [0.3, 0.5, 0.8, 0.9], P75 = 0.8 (nearest rank).
        // Now add a signal just below threshold * (1 - (0.8 - 0.6)) = 10 * 0.8 = 8.0
        // But floor is 10 * 0.6 = 6.0
        accumulator.addSignals(java.util.List.of(
                new io.casehub.neocortex.mindmap.AttentionSignal(
                        "agent-a", "t1", io.casehub.neocortex.mindmap.SignalCategory.PRIORITY_SHIFT,
                        "n5", "Test", 7.0, "test")));

        // With P75=0.8, adjusted threshold = 10 * (1 - 0.2) = 8.0
        // 7.0 < 8.0, so should NOT fire if P75 was updated
        // Without P75 update, threshold = 10.0, still no fire (7 < 10)
        // But let's verify by pushing over the adjusted threshold
        accumulator.addSignals(java.util.List.of(
                new io.casehub.neocortex.mindmap.AttentionSignal(
                        "agent-a", "t1", io.casehub.neocortex.mindmap.SignalCategory.DECAY_DETECTED,
                        "n6", "Test2", 1.5, "test")));

        // 7.0 + 1.5 = 8.5 > 8.0 (adjusted) — should fire with P75
        // Without P75 update: 8.5 < 10.0 — would NOT fire
        assertThat(firedEvents).hasSize(1);
    }

}
