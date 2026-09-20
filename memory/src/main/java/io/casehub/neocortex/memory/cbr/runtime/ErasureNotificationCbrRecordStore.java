package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.cbr.CbrRecordErased;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.DelegatingCbrRecordStore;
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
public class ErasureNotificationCbrRecordStore extends DelegatingCbrRecordStore {
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(ErasureNotificationCbrRecordStore.class.getName());


    private final Event<CbrRecordErased.ByRequest> byRequestEvent;
    private final Event<CbrRecordErased.ByEntity>  byEntityEvent;
    private final Event<CbrRecordErased.ByScope>   byScopeEvent;
    private final Clock                            clock;

    @Inject
    public ErasureNotificationCbrRecordStore(
            @Delegate @Any CbrRecordStore delegate,
            Event<CbrRecordErased.ByRequest> byRequestEvent,
            Event<CbrRecordErased.ByEntity> byEntityEvent,
            Event<CbrRecordErased.ByScope> byScopeEvent) {
        this(delegate, byRequestEvent, byEntityEvent, byScopeEvent, Clock.systemUTC());
    }

    ErasureNotificationCbrRecordStore(
            CbrRecordStore delegate,
            Event<CbrRecordErased.ByRequest> byRequestEvent,
            Event<CbrRecordErased.ByEntity> byEntityEvent,
            Event<CbrRecordErased.ByScope> byScopeEvent,
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
            safeFire(byRequestEvent, new CbrRecordErased.ByRequest(
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
            safeFire(byEntityEvent, new CbrRecordErased.ByEntity(
                    tenantId, count, entityId, Instant.now(clock)));
        }
        return count;
    }

    @Override
    public Integer eraseSubject(io.casehub.neocortex.memory.Subject subject, String tenantId) {
        int count = delegate.eraseSubject(subject, tenantId);
        if (count > 0) {
            safeFire(byEntityEvent, new CbrRecordErased.ByEntity(
                    tenantId, count, subject, Instant.now(clock)));
        }
        return count;
    }


    @Override
    public Integer eraseByScope(Path scope, String tenantId) {
        int count = delegate.eraseByScope(scope, tenantId);
        if (count > 0) {
            safeFire(byScopeEvent, new CbrRecordErased.ByScope(
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
