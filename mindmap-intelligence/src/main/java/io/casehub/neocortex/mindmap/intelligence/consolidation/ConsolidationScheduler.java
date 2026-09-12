package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryCapability;
import io.casehub.neocortex.mindmap.intelligence.CuriositySignal;
import io.casehub.neocortex.mindmap.intelligence.CuriositySignalGenerator;
import io.casehub.neocortex.mindmap.runtime.IdleTracker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
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
    private final ReentrantLock lock = new ReentrantLock();
    private final long intervalMinutes;
    private ScheduledExecutorService executor;

    @Inject
    ConsolidationScheduler(Instance<ConsolidationPhase> phases,
                           IdleTracker idleTracker,
                           CaseMemoryStore memoryStore,
                           Instance<CuriositySignalGenerator> curiosityGenerator,
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
            intervalMinutes);
    }

    ConsolidationScheduler(List<ConsolidationPhase> phases,
                           IdleTracker idleTracker,
                           CaseMemoryStore memoryStore,
                           CuriositySignalGenerator curiosityGenerator,
                           long intervalMinutes) {
        this.phases = phases;
        this.idleTracker = idleTracker;
        this.memoryStore = memoryStore;
        this.curiosityGenerator = curiosityGenerator;
        this.intervalMinutes = intervalMinutes;
    }

    ConsolidationScheduler(List<ConsolidationPhase> phases,
                           IdleTracker idleTracker,
                           CaseMemoryStore memoryStore,
                           CuriositySignalGenerator curiosityGenerator) {
        this(phases, idleTracker, memoryStore, curiosityGenerator, 5);
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
        if (executor != null) executor.shutdown();
    }

    void tick() {
        if (!lock.tryLock()) return;
        try {
            if (!idleTracker.isIdle(Duration.ofMinutes(1))) return;
            if (!memoryStore.capabilities()
                    .contains(MemoryCapability.DISCOVER_TENANTS)) {
                return;
            }

            for (ConsolidationPhase phase : phases) {
                if (phase instanceof AccessFrequencyPhase afp) {
                    afp.beginTick();
                }
            }

            for (String tenantId : memoryStore.discoverTenants(null, null)) {
                List<String> priority = subgraphPriority(tenantId);
                for (ConsolidationPhase phase : phases) {
                    try {
                        phase.run(tenantId, priority);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Phase " + phase.name()
                            + " failed for tenant " + tenantId, e);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void consolidateNow(String tenantId) {
        if (!lock.tryLock()) {
            LOG.info("Consolidation already running — skipping on-demand request for " + tenantId);
            return;
        }
        try {
            for (ConsolidationPhase phase : phases) {
                if (phase instanceof AccessFrequencyPhase afp) {
                    afp.beginTick();
                }
            }
            List<String> priority = subgraphPriority(tenantId);
            for (ConsolidationPhase phase : phases) {
                try {
                    phase.run(tenantId, priority);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Phase " + phase.name()
                                           + " failed for tenant " + tenantId, e);
                }
            }
        } finally {
            lock.unlock();
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
