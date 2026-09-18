package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrCasesErased;
import io.casehub.neocortex.memory.cbr.DelegatingCbrCaseMemoryStore;
import io.casehub.platform.api.path.Path;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Instant;

@Decorator
@Priority(45)
public class ErasureNotificationCbrCaseMemoryStore extends DelegatingCbrCaseMemoryStore {
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(ErasureNotificationCbrCaseMemoryStore.class.getName());


    private final Event<CbrCasesErased.ByRequest> byRequestEvent;
    private final Event<CbrCasesErased.ByEntity> byEntityEvent;
    private final Event<CbrCasesErased.ByScope> byScopeEvent;
    private final Clock clock;

    @Inject
    public ErasureNotificationCbrCaseMemoryStore(
            @Delegate @Any CbrCaseMemoryStore delegate,
            Event<CbrCasesErased.ByRequest> byRequestEvent,
            Event<CbrCasesErased.ByEntity> byEntityEvent,
            Event<CbrCasesErased.ByScope> byScopeEvent) {
        this(delegate, byRequestEvent, byEntityEvent, byScopeEvent, Clock.systemUTC());
    }

    ErasureNotificationCbrCaseMemoryStore(
            CbrCaseMemoryStore delegate,
            Event<CbrCasesErased.ByRequest> byRequestEvent,
            Event<CbrCasesErased.ByEntity> byEntityEvent,
            Event<CbrCasesErased.ByScope> byScopeEvent,
            Clock clock) {
        super(delegate);
        this.byRequestEvent = byRequestEvent;
        this.byEntityEvent = byEntityEvent;
        this.byScopeEvent = byScopeEvent;
        this.clock = clock;
    }

    @Override
    public Integer erase(EraseRequest request) {
        int count = delegate.erase(request);
        if (count > 0) {
            safeFire(byRequestEvent, new CbrCasesErased.ByRequest(
                    request.tenantId(), count, request.subject(),
                    request.domain(), request.caseId(),
                    Instant.now(clock)));
        }
        return count;
    }

    @Override
    public Integer eraseEntity(String entityId, String tenantId) {
        int count = delegate.eraseEntity(entityId, tenantId);
        if (count > 0) {
            safeFire(byEntityEvent, new CbrCasesErased.ByEntity(
                    tenantId, count, entityId, Instant.now(clock)));
        }
        return count;
    }

    @Override
    public Integer eraseSubject(io.casehub.neocortex.memory.Subject subject, String tenantId) {
        int count = delegate.eraseSubject(subject, tenantId);
        if (count > 0) {
            safeFire(byEntityEvent, new CbrCasesErased.ByEntity(
                    tenantId, count, subject, Instant.now(clock)));
        }
        return count;
    }


    @Override
    public Integer eraseByScope(Path scope, String tenantId) {
        int count = delegate.eraseByScope(scope, tenantId);
        if (count > 0) {
            safeFire(byScopeEvent, new CbrCasesErased.ByScope(
                    tenantId, count, scope, Instant.now(clock)));
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
