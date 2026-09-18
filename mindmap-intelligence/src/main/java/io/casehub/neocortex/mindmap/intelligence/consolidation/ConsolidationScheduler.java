package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryCapability;
import io.casehub.neocortex.mindmap.intelligence.CuriositySignal;
import io.casehub.neocortex.mindmap.intelligence.CuriositySignalGenerator;
import io.casehub.neocortex.mindmap.runtime.IdleTracker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.casehub.neocortex.mindmap.MutationContext;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class ConsolidationScheduler {

    private static final Logger LOG = Logger.getLogger(
        ConsolidationScheduler.class.getName());

    private final List<ConsolidationPhase> phases;
    private final IdleTracker idleTracker;
    private final CaseMemoryStore memoryStore;
    private final CuriositySignalGenerator curiosityGenerator;
    private final Consumer<ConsolidationCompleted> completionSink;
    private final SignificanceAccumulator significanceAccumulator;
    private final ReentrantLock lock = new ReentrantLock();
    private final long intervalMinutes;
    private volatile ScheduledExecutorService executor;

    @Inject
    ConsolidationScheduler(Instance<ConsolidationPhase> phases,
                           IdleTracker idleTracker,
                           CaseMemoryStore memoryStore,
                           Instance<CuriositySignalGenerator> curiosityGenerator,
                           Instance<SignificanceAccumulator> significanceAccumulator,
                           Event<ConsolidationCompleted> completionEvent,
                           @ConfigProperty(name = "casehub.consolidation.interval-minutes",
                                           defaultValue = "5") long intervalMinutes) {
        this(phases.stream()
                .sorted(Comparator.comparingInt(p ->
                    Optional.ofNullable(p.getClass().getAnnotation(
                        jakarta.annotation.Priority.class))
                        .map(jakarta.annotation.Priority::value)
                        .orElse(Integer.MAX_VALUE)))
                .toList(),
            idleTracker, memoryStore,
            curiosityGenerator.isResolvable() ? curiosityGenerator.get() : null,
            completionEvent::fire,
            intervalMinutes,
            significanceAccumulator.isResolvable() ? significanceAccumulator.get() : null);
    }

    ConsolidationScheduler(List<ConsolidationPhase> phases,
                           IdleTracker idleTracker,
                           CaseMemoryStore memoryStore,
                           CuriositySignalGenerator curiosityGenerator,
                           Consumer<ConsolidationCompleted> completionSink,
                           long intervalMinutes,
                           SignificanceAccumulator significanceAccumulator) {
        this.phases = phases;
        this.idleTracker = idleTracker;
        this.memoryStore = memoryStore;
        this.curiosityGenerator = curiosityGenerator;
        this.completionSink = completionSink;
        this.intervalMinutes = intervalMinutes;
        this.significanceAccumulator = significanceAccumulator;
    }

    ConsolidationScheduler(List<ConsolidationPhase> phases,
                           IdleTracker idleTracker,
                           CaseMemoryStore memoryStore,
                           CuriositySignalGenerator curiosityGenerator) {
        this(phases, idleTracker, memoryStore, curiosityGenerator, e -> {}, 5, null);
    }

    @PostConstruct
    void start() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "consolidation-scheduler");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::tick, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
        LOG.info("Consolidation scheduler started: interval=" + intervalMinutes + "m");
    }

    @PreDestroy
    void stop() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    LOG.warning("Consolidation scheduler did not terminate within 30s");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    void tick() {
        try {
            if (!lock.tryLock()) {return;}
            try {
                if (!idleTracker.isIdle(Duration.ofMinutes(1))) {return;}
                if (!memoryStore.capabilities()
                                .contains(MemoryCapability.DISCOVER_TENANTS)) {
                    return;
                }
                beginTickAllPhases();
                for (String tenantId : memoryStore.discoverTenants(null, null)) {
                    runPhases(tenantId);
                }
            } finally {
                lock.unlock();
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Consolidation tick failed — scheduler will retry next interval", e);
        }
    }

    public void consolidateNow(String tenantId) {
        if (!lock.tryLock()) {
            LOG.info("Consolidation already running — skipping on-demand request for " + tenantId);
            return;
        }
        try {
            beginTickAllPhases();
            runPhases(tenantId);
        } finally {
            lock.unlock();
        }
    }


    private void runPhases(String tenantId) {
        List<String>      priority     = subgraphPriority(tenantId);
        List<PhaseResult> phaseResults = new ArrayList<>();
        for (ConsolidationPhase phase : phases) {
            Instant phaseStart = Instant.now();
            MutationContext.set("consolidation:" + phase.name());
            try {
                phase.run(tenantId, priority);
                phaseResults.add(new PhaseResult(phase.name(), phaseStart, Instant.now(), true, null));
            } catch (Exception e) {
                phaseResults.add(new PhaseResult(phase.name(), phaseStart, Instant.now(), false, e.getMessage()));
                LOG.log(Level.WARNING, "Phase " + phase.name()
                                       + " failed for tenant " + tenantId, e);
            } finally {
                MutationContext.clear();
            }
        }
        completionSink.accept(new ConsolidationCompleted(tenantId, phaseResults));
    }

    private void beginTickAllPhases() {
        if (significanceAccumulator != null) {
            significanceAccumulator.swapAndReset();
        }
        for (ConsolidationPhase phase : phases) {
            phase.beginTick();
        }
    }

    private List<String> subgraphPriority(String tenantId) {
        if (curiosityGenerator == null) return List.of();
        return curiosityGenerator.computeSignals(tenantId, Set.of()).stream()
            .map(CuriositySignal::targetSubgraphId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    }
}
