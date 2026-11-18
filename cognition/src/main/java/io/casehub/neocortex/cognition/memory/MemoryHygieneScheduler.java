package io.casehub.neocortex.cognition.memory;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.reflection.ReflectionOrchestrator;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MemoryHygieneScheduler {

    private static final Logger LOG = Logger.getLogger(MemoryHygieneScheduler.class.getName());

    private final MemoryHygieneOrchestratorImpl orchestrator;
    private final ReflectionOrchestrator reflectionOrchestrator;
    private final IntegrityChecker integrityChecker;
    private final MemoryDomain domain;
    private final int maxReflectionSources;
    private final Consumer<HygieneEvent> eventSink;

    public MemoryHygieneScheduler(
            MemoryHygieneOrchestratorImpl orchestrator,
            ReflectionOrchestrator reflectionOrchestrator,
            IntegrityChecker integrityChecker,
            MemoryDomain domain,
            int maxReflectionSources,
            Consumer<HygieneEvent> eventSink) {
        this.orchestrator = orchestrator;
        this.reflectionOrchestrator = reflectionOrchestrator;
        this.integrityChecker = integrityChecker;
        this.domain = domain;
        this.maxReflectionSources = maxReflectionSources;
        this.eventSink = eventSink;
    }

    public MaintenanceTick maintain(String agentId, String tenantId) {
        var hygieneTick = orchestrator.doTick(agentId, tenantId);

        if (hygieneTick instanceof HygieneTick.Failed f) {
            return new MaintenanceTick.Failed("tick", f.reason());
        }

        int reflectionsGenerated;
        try {
            var since = Instant.now().minusSeconds(86400);
            var reflections = reflectionOrchestrator.reflect(
                    agentId, tenantId, since, maxReflectionSources);
            reflectionsGenerated = reflections.size();
            for (var insight : reflections) {
                eventSink.accept(new HygieneEvent.ReflectionGenerated(agentId, insight));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Reflection failed for " + agentId, e);
            return new MaintenanceTick.Failed("reflection", e.getMessage());
        }

        List<IntegrityViolation> violations;
        try {
            violations = integrityChecker.check(agentId, tenantId, domain);
            for (var violation : violations) {
                eventSink.accept(new HygieneEvent.IntegrityViolationDetected(violation));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Integrity check failed for " + agentId, e);
            return new MaintenanceTick.Failed("integrity", e.getMessage());
        }

        return new MaintenanceTick.Completed(hygieneTick, reflectionsGenerated, 0, violations);
    }
}
