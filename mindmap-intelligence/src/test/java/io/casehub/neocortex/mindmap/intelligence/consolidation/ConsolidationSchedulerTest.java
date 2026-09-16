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
                5, accumulator);

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
                5, accumulator);

        var event = new io.casehub.neocortex.memory.experience.ExperienceRecorded(
                new io.casehub.neocortex.memory.experience.Observation("a1", "tenant-1", null, "t1",
                                                                       java.time.Instant.now(), "test event", null, java.util.Map.of(), "entity-1"),
                "m1");
        accumulator.onExperienceRecorded(event);

        schedulerWithAccumulator.consolidateNow("tenant-1");

        SignificanceSnapshot snapshot = accumulator.swapAndReset();
        assertThat(snapshot.perTenant()).isEmpty();
    }
}
