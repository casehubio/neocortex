package io.casehub.neocortex.cognition.memory;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.reflection.ReflectionOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MemoryHygieneSchedulerTest {

    private MemoryHygieneOrchestratorImpl orchestrator;
    private ReflectionOrchestrator reflectionOrchestrator;
    private IntegrityChecker integrityChecker;
    @SuppressWarnings("unchecked")
    private Consumer<HygieneEvent> eventSink = mock(Consumer.class);
    private MemoryHygieneScheduler scheduler;

    private static final MemoryDomain DOMAIN = new MemoryDomain("agent");

    @BeforeEach
    void setUp() {
        orchestrator = mock(MemoryHygieneOrchestratorImpl.class);
        reflectionOrchestrator = mock(ReflectionOrchestrator.class);
        integrityChecker = mock(IntegrityChecker.class);
        scheduler = new MemoryHygieneScheduler(
                orchestrator, reflectionOrchestrator,
                integrityChecker, DOMAIN, 50, eventSink);
    }

    @Test
    void maintainComposesAllStages() {
        var tick = new HygieneTick.Completed(0, 1, 5, List.of());
        when(orchestrator.doTick("agent-1", "tenant-1")).thenReturn(tick);
        when(reflectionOrchestrator.reflect(anyString(), anyString(), any(Instant.class), anyInt()))
                .thenReturn(List.of("insight one"));
        when(integrityChecker.check(anyString(), anyString(), any(MemoryDomain.class)))
                .thenReturn(List.of());

        var result = scheduler.maintain("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(MaintenanceTick.Completed.class);
        var completed = (MaintenanceTick.Completed) result;
        assertThat(completed.reflectionsGenerated()).isEqualTo(1);
        assertThat(completed.hygiene()).isEqualTo(tick);
    }

    @Test
    void maintainReturnsFailedOnReflectionError() {
        when(orchestrator.doTick(anyString(), anyString()))
                .thenReturn(new HygieneTick.Completed(0, 0, 0, List.of()));
        when(reflectionOrchestrator.reflect(anyString(), anyString(), any(Instant.class), anyInt()))
                .thenThrow(new RuntimeException("reflection failed"));

        var result = scheduler.maintain("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(MaintenanceTick.Failed.class);
        assertThat(((MaintenanceTick.Failed) result).stage()).isEqualTo("reflection");
    }

    @Test
    void maintainPropagatesTickFailure() {
        when(orchestrator.doTick(anyString(), anyString()))
                .thenReturn(new HygieneTick.Failed("store down"));

        var result = scheduler.maintain("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(MaintenanceTick.Failed.class);
        assertThat(((MaintenanceTick.Failed) result).stage()).isEqualTo("tick");
    }

    @Test
    void maintainReportsIntegrityViolations() {
        when(orchestrator.doTick(anyString(), anyString()))
                .thenReturn(new HygieneTick.Completed(0, 0, 0, List.of()));
        when(reflectionOrchestrator.reflect(anyString(), anyString(), any(Instant.class), anyInt()))
                .thenReturn(List.of());
        var violation = new IntegrityViolation("c1", ViolationType.ORPHANED_SUPERSESSION, "orphaned", false);
        when(integrityChecker.check(anyString(), anyString(), any(MemoryDomain.class)))
                .thenReturn(List.of(violation));

        var result = scheduler.maintain("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(MaintenanceTick.Completed.class);
        assertThat(((MaintenanceTick.Completed) result).violations()).hasSize(1);
        verify(eventSink).accept(any(HygieneEvent.IntegrityViolationDetected.class));
    }

    @Test
    void maintainHandlesIdleTick() {
        when(orchestrator.doTick(anyString(), anyString()))
                .thenReturn(new HygieneTick.Idle("no memories"));
        when(reflectionOrchestrator.reflect(anyString(), anyString(), any(Instant.class), anyInt()))
                .thenReturn(List.of());
        when(integrityChecker.check(anyString(), anyString(), any(MemoryDomain.class)))
                .thenReturn(List.of());

        var result = scheduler.maintain("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(MaintenanceTick.Completed.class);
    }
}
