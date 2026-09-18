package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrCasesReinstated;
import io.casehub.neocortex.memory.cbr.CbrCasesSuperseded;
import io.casehub.neocortex.memory.cbr.CbrFilter;
import io.casehub.neocortex.memory.cbr.DelegatingCbrCaseMemoryStore;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;

@Decorator
@Priority(44)
public class SupersessionNotificationCbrCaseMemoryStore extends DelegatingCbrCaseMemoryStore {
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(SupersessionNotificationCbrCaseMemoryStore.class.getName());


    private final Event<CbrCasesSuperseded> supersededEvent;
    private final Event<CbrCasesReinstated> reinstatedEvent;
    private final Clock clock;

    @Inject
    public SupersessionNotificationCbrCaseMemoryStore(
            @Delegate @Any CbrCaseMemoryStore delegate,
            Event<CbrCasesSuperseded> supersededEvent,
            Event<CbrCasesReinstated> reinstatedEvent) {
        this(delegate, supersededEvent, reinstatedEvent, Clock.systemUTC());
    }

    SupersessionNotificationCbrCaseMemoryStore(
            CbrCaseMemoryStore delegate,
            Event<CbrCasesSuperseded> supersededEvent,
            Event<CbrCasesReinstated> reinstatedEvent,
            Clock clock) {
        super(delegate);
        this.supersededEvent = supersededEvent;
        this.reinstatedEvent = reinstatedEvent;
        this.clock = clock;
    }

    @Override
    public boolean supersede(String caseId, String tenantId, String supersedingCaseId, String reason) {
        boolean result = delegate.supersede(caseId, tenantId, supersedingCaseId, reason);
        if (result) {
            safeFire(supersededEvent, new CbrCasesSuperseded.ByCase(
                    tenantId, caseId, supersedingCaseId, reason, Instant.now(clock)));
        }
        return result;
    }

    @Override
    public int supersedeMatching(String tenantId, MemoryDomain domain, String caseType,
                                  Map<String, CbrFilter> filters, String reason) {
        int count = delegate.supersedeMatching(tenantId, domain, caseType, filters, reason);
        if (count > 0) {
            safeFire(supersededEvent, new CbrCasesSuperseded.ByFilter(
                    tenantId, domain, caseType, filters, reason, count, Instant.now(clock)));
        }
        return count;
    }

    @Override
    public int supersedeAll(Collection<String> caseIds, String tenantId, String reason) {
        int count = delegate.supersedeAll(caseIds, tenantId, reason);
        if (count > 0) {
            safeFire(supersededEvent, new CbrCasesSuperseded.ByIds(
                    tenantId, caseIds, reason, count, Instant.now(clock)));
        }
        return count;
    }

    @Override
    public boolean reinstate(String caseId, String tenantId) {
        boolean result = delegate.reinstate(caseId, tenantId);
        if (result) {
            safeFire(reinstatedEvent, new CbrCasesReinstated.ByCase(
                    tenantId, caseId, Instant.now(clock)));
        }
        return result;
    }

    @Override
    public int reinstateMatching(String tenantId, MemoryDomain domain, String caseType,
                                  Map<String, CbrFilter> filters) {
        int count = delegate.reinstateMatching(tenantId, domain, caseType, filters);
        if (count > 0) {
            safeFire(reinstatedEvent, new CbrCasesReinstated.ByFilter(
                    tenantId, domain, caseType, filters, count, Instant.now(clock)));
        }
        return count;
    }

    @Override
    public int reinstateAll(Collection<String> caseIds, String tenantId) {
        int count = delegate.reinstateAll(caseIds, tenantId);
        if (count > 0) {
            safeFire(reinstatedEvent, new CbrCasesReinstated.ByIds(
                    tenantId, caseIds, count, Instant.now(clock)));
        }
        return count;
    }

    private static <T> void safeFire(Event<T> event, T payload) {
        try {
            event.fire(payload);
        } catch (Exception e) {
            LOG.log(java.util.logging.Level.WARNING, "CDI event notification failed", e);
        }
    }
}
