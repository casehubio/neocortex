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
        scheduler.consolidateNow("tenant-1");
        executedPhases.clear();
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
        schedulerWithFailure.consolidateNow("tenant-1");
        executedPhases.clear();
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

        schedulerWithAccumulator.consolidateNow("tenant-1");
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
        var registry = io.casehub.neocortex.cognitive.index.EffectiveCognitiveDefaults.forTesting(
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
        var registry = io.casehub.neocortex.cognitive.index.EffectiveCognitiveDefaults.forTesting(
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


    @Test
    void runPhases_passes_journal_to_phases() {
        var capturedJournal = new java.util.concurrent.atomic.AtomicReference<io.casehub.neocortex.cognition.journal.CognitiveJournal>();
        ConsolidationPhase phase = new ConsolidationPhase() {
            @Override
            public String name()                                        {return "journal-phase";}

            @Override
            public void run(String tenantId, java.util.List<String> sp) {}

            @Override
            public void setCognitiveJournal(io.casehub.neocortex.cognition.journal.CognitiveJournal journal) {
                capturedJournal.set(journal);
            }
        };

        io.casehub.neocortex.cognition.journal.CognitiveJournal journal = entry -> {};
        var testScheduler = new ConsolidationScheduler(
                java.util.List.of(phase), idleTracker, memoryStore, null);
        testScheduler.setCognitiveJournal(journal);
        testScheduler.consolidateNow("tenant-1");

        assertThat(capturedJournal.get()).isSameAs(journal);
    }

    @Test
    void runPhases_passes_sessionPipeline_to_phases() {
        var capturedPipeline = new java.util.concurrent.atomic.AtomicReference<io.casehub.platform.agent.session.SessionPipeline>();
        ConsolidationPhase phase = new ConsolidationPhase() {
            @Override
            public String name() {return "pipeline-phase";}

            @Override
            public void run(String tenantId, java.util.List<String> sp) {}

            @Override
            public void setSessionPipeline(io.casehub.platform.agent.session.SessionPipeline pipeline) {
                capturedPipeline.set(pipeline);
            }
        };

        var testScheduler = new ConsolidationScheduler(
                java.util.List.of(phase), idleTracker, memoryStore, null);
        assertThat(testScheduler.hasSessionPipeline()).isFalse();

        testScheduler.consolidateNow("tenant-1");
        assertThat(capturedPipeline.get()).as("phase receives null when no pipeline set").isNull();
    }


    @Test
    void phase_can_record_entries_via_journal() {
        var                                                     entries = new java.util.ArrayList<io.casehub.neocortex.cognition.journal.CognitiveEntry>();
        io.casehub.neocortex.cognition.journal.CognitiveJournal journal = entries::add;

        ConsolidationPhase phase = new ConsolidationPhase() {
            private io.casehub.neocortex.cognition.journal.CognitiveJournal journal =
                    io.casehub.neocortex.cognition.journal.CognitiveJournal.NOOP;

            @Override
            public String name() {return "recording-phase";}

            @Override
            public void setCognitiveJournal(io.casehub.neocortex.cognition.journal.CognitiveJournal j) {
                this.journal = j;
            }

            @Override
            public void run(String tenantId, java.util.List<String> sp) {
                journal.record(new io.casehub.neocortex.cognition.journal.CognitiveEntry.Consolidated(
                        tenantId, "_system",
                        io.casehub.neocortex.cognition.journal.EventRef.consolidation(
                                io.casehub.neocortex.cognition.journal.EventRef.init()),
                        java.time.Instant.now(),
                        5, 2, 100, name()));
            }
        };

        var testScheduler = new ConsolidationScheduler(
                java.util.List.of(phase), idleTracker, memoryStore, null);
        testScheduler.setCognitiveJournal(journal);
        testScheduler.consolidateNow("tenant-1");

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0)).isInstanceOf(io.casehub.neocortex.cognition.journal.CognitiveEntry.Consolidated.class);
        var consolidated = (io.casehub.neocortex.cognition.journal.CognitiveEntry.Consolidated) entries.get(0);
        assertThat(consolidated.phase()).isEqualTo("recording-phase");
        assertThat(consolidated.inputNodes()).isEqualTo(5);
        assertThat(consolidated.tenantId()).isEqualTo("tenant-1");
    }

    @Test
    void runPhases_with_journal_does_not_break_phases_that_ignore_it() {
        io.casehub.neocortex.cognition.journal.CognitiveJournal journal = entry -> {};
        scheduler.setCognitiveJournal(journal);
        scheduler.tick();
        assertThat(executedPhases).containsExactly(
                "phase-1:tenant-1", "phase-2:tenant-1");
    }

    @Test
    void runPhases_records_consolidated_entry_per_successful_phase() {
        var                                                     entries = new java.util.ArrayList<io.casehub.neocortex.cognition.journal.CognitiveEntry>();
        io.casehub.neocortex.cognition.journal.CognitiveJournal journal = entries::add;

        ConsolidationPhase alpha = new ConsolidationPhase() {
            @Override
            public String name()                                        {return "alpha";}

            @Override
            public void run(String tenantId, java.util.List<String> sp) {}

            @Override
            public java.util.List<io.casehub.neocortex.mindmap.ConsolidationArtifact> artifacts() {
                return java.util.List.of(
                        new io.casehub.neocortex.mindmap.ConsolidationArtifact.MergePerformed(
                                "tenant-1", "n1", "n2", 0.9, "similar", java.time.Instant.now()));
            }
        };
        ConsolidationPhase beta = new ConsolidationPhase() {
            @Override
            public String name()                                        {return "beta";}

            @Override
            public void run(String tenantId, java.util.List<String> sp) {}
        };

        var testScheduler = new ConsolidationScheduler(
                java.util.List.of(alpha, beta), idleTracker, memoryStore, null);
        testScheduler.setCognitiveJournal(journal);
        testScheduler.consolidateNow("tenant-1");

        var consolidated = entries.stream()
                                  .filter(e -> e instanceof io.casehub.neocortex.cognition.journal.CognitiveEntry.Consolidated)
                                  .map(e -> (io.casehub.neocortex.cognition.journal.CognitiveEntry.Consolidated) e)
                                  .toList();
        assertThat(consolidated).hasSize(2);

        assertThat(consolidated.get(0).phase()).isEqualTo("alpha");
        assertThat(consolidated.get(0).outputNodes()).isEqualTo(1);
        assertThat(consolidated.get(0).tenantId()).isEqualTo("tenant-1");
        assertThat(consolidated.get(0).agentId()).isEqualTo("_system");
        assertThat(consolidated.get(0).elapsedMs()).isGreaterThanOrEqualTo(0);

        assertThat(consolidated.get(1).phase()).isEqualTo("beta");
        assertThat(consolidated.get(1).outputNodes()).isEqualTo(0);
    }

    @Test
    void runPhases_skips_journal_entry_for_failed_phase() {
        var                                                     entries = new java.util.ArrayList<io.casehub.neocortex.cognition.journal.CognitiveEntry>();
        io.casehub.neocortex.cognition.journal.CognitiveJournal journal = entries::add;

        ConsolidationPhase failing = new ConsolidationPhase() {
            @Override
            public String name() {return "failing";}

            @Override
            public void run(String tenantId, java.util.List<String> sp) {
                throw new RuntimeException("boom");
            }
        };
        ConsolidationPhase succeeding = new ConsolidationPhase() {
            @Override
            public String name()                                        {return "succeeding";}

            @Override
            public void run(String tenantId, java.util.List<String> sp) {}
        };

        var testScheduler = new ConsolidationScheduler(
                java.util.List.of(failing, succeeding), idleTracker, memoryStore, null);
        testScheduler.setCognitiveJournal(journal);
        testScheduler.consolidateNow("tenant-1");

        var consolidated = entries.stream()
                                  .filter(e -> e instanceof io.casehub.neocortex.cognition.journal.CognitiveEntry.Consolidated)
                                  .map(e -> (io.casehub.neocortex.cognition.journal.CognitiveEntry.Consolidated) e)
                                  .toList();
        assertThat(consolidated).hasSize(1);
        assertThat(consolidated.get(0).phase()).isEqualTo("succeeding");
    }

    @Test
    void runPhases_journal_failure_does_not_corrupt_phase_results() {
        io.casehub.neocortex.cognition.journal.CognitiveJournal throwingJournal = entry -> {
            throw new RuntimeException("journal broken");
        };

        var testScheduler = new ConsolidationScheduler(
                java.util.List.of(
                        new ConsolidationPhase() {
                            @Override
                            public String name() {return "resilient";}

                            @Override
                            public void run(String tenantId, java.util.List<String> sp) {
                                executedPhases.add("resilient:" + tenantId);
                            }
                        }
                                 ), idleTracker, memoryStore, null);
        testScheduler.setCognitiveJournal(throwingJournal);
        testScheduler.consolidateNow("tenant-1");

        assertThat(executedPhases).containsExactly("resilient:tenant-1");
    }

    @Test
    void consolidation_tick_counter_increments_in_eventRef() {
        var                                                     entries = new java.util.ArrayList<io.casehub.neocortex.cognition.journal.CognitiveEntry>();
        io.casehub.neocortex.cognition.journal.CognitiveJournal journal = entries::add;

        ConsolidationPhase phase = new ConsolidationPhase() {
            @Override
            public String name()                                        {return "counter-phase";}

            @Override
            public void run(String tenantId, java.util.List<String> sp) {}
        };

        var testScheduler = new ConsolidationScheduler(
                java.util.List.of(phase), idleTracker, memoryStore, null);
        testScheduler.setCognitiveJournal(journal);

        testScheduler.consolidateNow("tenant-1");
        testScheduler.consolidateNow("tenant-1");

        var consolidated = entries.stream()
                                  .filter(e -> e instanceof io.casehub.neocortex.cognition.journal.CognitiveEntry.Consolidated)
                                  .map(e -> (io.casehub.neocortex.cognition.journal.CognitiveEntry.Consolidated) e)
                                  .toList();
        assertThat(consolidated).hasSize(2);

        assertThat(consolidated.get(0).eventRef().toString()).isEqualTo("consolidation:tick:1");
        assertThat(consolidated.get(1).eventRef().toString()).isEqualTo("consolidation:tick:2");
    }

    @Test
    void registerTenant_makesKnownWithoutRunningPhases() {
        var testScheduler = new ConsolidationScheduler(
                List.of(new ConsolidationPhase() {
                    @Override
                    public String name() {return "tracked";}

                    @Override
                    public void run(String tenantId, java.util.List<String> sp) {
                        executedPhases.add("tracked:" + tenantId);
                    }
                }), idleTracker, memoryStore, null);

        testScheduler.registerTenant("tenant-2");

        assertThat(executedPhases).isEmpty();

        testScheduler.tick();
        assertThat(executedPhases).containsExactly("tracked:tenant-2");
    }

    @Test
    void tick_whenNotIdle_runsStarvedTenants() {
        var testScheduler = new ConsolidationScheduler(
                List.of(new ConsolidationPhase() {
                    @Override
                    public String name() {return "starved";}

                    @Override
                    public void run(String tenantId, java.util.List<String> sp) {
                        executedPhases.add("starved:" + tenantId);
                    }
                }), idleTracker, memoryStore, null, e -> {}, 5, null, null, 3);

        testScheduler.registerTenant("tenant-1");

        idleTracker.recordWrite();
        testScheduler.tick();
        assertThat(executedPhases).as("first tick while busy — not starved yet").isEmpty();

        testScheduler.tick();
        assertThat(executedPhases).as("second tick while busy — not starved yet").isEmpty();

        testScheduler.tick();
        assertThat(executedPhases).as("third tick while busy — now starved, should run")
                                  .containsExactly("starved:tenant-1");
    }

    @Test
    void seedTenantsFromStore_populatesKnownTenants() {
        var testScheduler = new ConsolidationScheduler(
                List.of(new ConsolidationPhase() {
                    @Override
                    public String name() {return "seeded";}

                    @Override
                    public void run(String tenantId, java.util.List<String> sp) {
                        executedPhases.add("seeded:" + tenantId);
                    }
                }), idleTracker, memoryStore, null);

        testScheduler.seedTenantsFromStore();

        testScheduler.tick();
        assertThat(executedPhases).containsExactly("seeded:tenant-1");
    }


}
