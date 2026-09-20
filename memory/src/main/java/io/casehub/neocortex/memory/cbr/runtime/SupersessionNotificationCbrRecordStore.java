package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrRecordReinstated;
import io.casehub.neocortex.memory.cbr.CbrRecordSuperseded;
import io.casehub.neocortex.memory.cbr.CbrFilter;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.DelegatingCbrRecordStore;
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
public class SupersessionNotificationCbrRecordStore extends DelegatingCbrRecordStore {
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(SupersessionNotificationCbrRecordStore.class.getName());


    private final Event<CbrRecordSuperseded> supersededEvent;
    private final Event<CbrRecordReinstated> reinstatedEvent;
    private final Clock                      clock;

    @Inject
    public SupersessionNotificationCbrRecordStore(
            @Delegate @Any CbrRecordStore delegate,
            Event<CbrRecordSuperseded> supersededEvent,
            Event<CbrRecordReinstated> reinstatedEvent) {
        this(delegate, supersededEvent, reinstatedEvent, Clock.systemUTC());
    }

    SupersessionNotificationCbrRecordStore(
            CbrRecordStore delegate,
            Event<CbrRecordSuperseded> supersededEvent,
            Event<CbrRecordReinstated> reinstatedEvent,
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
            safeFire(supersededEvent, new CbrRecordSuperseded.ByCase(
                    tenantId, caseId, supersedingCaseId, reason, Instant.now(clock)));
        }
        return result;
    }

    @Override
    public int supersedeMatching(String tenantId, MemoryDomain domain, String caseType,
                                  Map<String, CbrFilter> filters, String reason) {
        int count = delegate.supersedeMatching(tenantId, domain, caseType, filters, reason);
        if (count > 0) {
            safeFire(supersededEvent, new CbrRecordSuperseded.ByFilter(
                    tenantId, domain, caseType, filters, reason, count, Instant.now(clock)));
        }
        return count;
    }

    @Override
    public int supersedeAll(Collection<String> caseIds, String tenantId, String reason) {
        int count = delegate.supersedeAll(caseIds, tenantId, reason);
        if (count > 0) {
            safeFire(supersededEvent, new CbrRecordSuperseded.ByIds(
                    tenantId, caseIds, reason, count, Instant.now(clock)));
        }
        return count;
    }

    @Override
    public boolean reinstate(String caseId, String tenantId) {
        boolean result = delegate.reinstate(caseId, tenantId);
        if (result) {
            safeFire(reinstatedEvent, new CbrRecordReinstated.ByCase(
                    tenantId, caseId, Instant.now(clock)));
        }
        return result;
    }

    @Override
    public int reinstateMatching(String tenantId, MemoryDomain domain, String caseType,
                                  Map<String, CbrFilter> filters) {
        int count = delegate.reinstateMatching(tenantId, domain, caseType, filters);
        if (count > 0) {
            safeFire(reinstatedEvent, new CbrRecordReinstated.ByFilter(
                    tenantId, domain, caseType, filters, count, Instant.now(clock)));
        }
        return count;
    }

    @Override
    public int reinstateAll(Collection<String> caseIds, String tenantId) {
        int count = delegate.reinstateAll(caseIds, tenantId);
        if (count > 0) {
            safeFire(reinstatedEvent, new CbrRecordReinstated.ByIds(
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
