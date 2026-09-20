package io.casehub.neocortex.memory.runtime;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.DelegatingCaseMemoryStore;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryEntityErased;
import io.casehub.neocortex.memory.Subject;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

@Decorator
@Priority(45)
public class ErasureNotificationCaseMemoryStore extends DelegatingCaseMemoryStore {
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(ErasureNotificationCaseMemoryStore.class.getName());

    private final Event<MemoryEntityErased.ByRequest> byRequestEvent;
    private final Event<MemoryEntityErased.ByEntity> byEntityEvent;
    private final Event<MemoryEntityErased.CrossTenant> crossTenantEvent;
    private final Clock clock;

    @Inject
    public ErasureNotificationCaseMemoryStore(
            @Delegate @Any CaseMemoryStore delegate,
            Event<MemoryEntityErased.ByRequest> byRequestEvent,
            Event<MemoryEntityErased.ByEntity> byEntityEvent,
            Event<MemoryEntityErased.CrossTenant> crossTenantEvent) {
        this(delegate, byRequestEvent, byEntityEvent, crossTenantEvent, Clock.systemUTC());
    }

    ErasureNotificationCaseMemoryStore(
            CaseMemoryStore delegate,
            Event<MemoryEntityErased.ByRequest> byRequestEvent,
            Event<MemoryEntityErased.ByEntity> byEntityEvent,
            Event<MemoryEntityErased.CrossTenant> crossTenantEvent,
            Clock clock) {
        super(delegate);
        this.byRequestEvent = byRequestEvent;
        this.byEntityEvent = byEntityEvent;
        this.crossTenantEvent = crossTenantEvent;
        this.clock = clock;
    }

    @Override
    public int erase(EraseRequest request) {
        int count = delegate.erase(request);
        if (count > 0) {
            safeFire(byRequestEvent, new MemoryEntityErased.ByRequest(
                    request.tenantId(), count, request.subject(),
                    request.domain(), Instant.now(clock)));
        }
        return count;
    }

    @Override
    public int eraseSubject(Subject subject, String tenantId) {
        int count = delegate.eraseSubject(subject, tenantId);
        if (count > 0) {
            safeFire(byEntityEvent, new MemoryEntityErased.ByEntity(
                    tenantId, count, subject, Instant.now(clock)));
        }
        return count;
    }

    @Deprecated(forRemoval = true)
    @Override
    public int eraseEntity(String entityId, String tenantId) {
        int count = delegate.eraseEntity(entityId, tenantId);
        if (count > 0) {
            safeFire(byEntityEvent, new MemoryEntityErased.ByEntity(
                    tenantId, count, entityId, Instant.now(clock)));
        }
        return count;
    }

    @Override
    public int eraseSubjectAcrossTenants(Subject subject, Set<String> tenantIds) {
        int count = delegate.eraseSubjectAcrossTenants(subject, tenantIds);
        if (count > 0) {
            safeFire(crossTenantEvent, new MemoryEntityErased.CrossTenant(
                    count, subject, tenantIds, Instant.now(clock)));
        }
        return count;
    }

    @Deprecated(forRemoval = true)
    @Override
    public int eraseEntityAcrossTenants(String entityId, Set<String> tenantIds) {
        int count = delegate.eraseEntityAcrossTenants(entityId, tenantIds);
        if (count > 0) {
            safeFire(crossTenantEvent, new MemoryEntityErased.CrossTenant(
                    count, entityId, tenantIds, Instant.now(clock)));
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
