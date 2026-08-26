package io.casehub.neocortex.memory.runtime;

import io.casehub.neocortex.memory.*;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Decorator
@Priority(45)
public class ErasureNotificationCaseMemoryStore implements CaseMemoryStore {

    private final CaseMemoryStore delegate;
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
        this.delegate = delegate;
        this.byRequestEvent = byRequestEvent;
        this.byEntityEvent = byEntityEvent;
        this.crossTenantEvent = crossTenantEvent;
        this.clock = clock;
    }

    @Override
    public int erase(EraseRequest request) {
        int count = delegate.erase(request);
        if (count > 0) {
            byRequestEvent.fire(new MemoryEntityErased.ByRequest(
                    request.tenantId(), count, request.entityId(),
                    request.domain(), Instant.now(clock)));
        }
        return count;
    }

    @Override
    public int eraseEntity(String entityId, String tenantId) {
        int count = delegate.eraseEntity(entityId, tenantId);
        if (count > 0) {
            byEntityEvent.fire(new MemoryEntityErased.ByEntity(
                    tenantId, count, entityId, Instant.now(clock)));
        }
        return count;
    }

    @Override
    public int eraseEntityAcrossTenants(String entityId, Set<String> tenantIds) {
        int count = delegate.eraseEntityAcrossTenants(entityId, tenantIds);
        if (count > 0) {
            crossTenantEvent.fire(new MemoryEntityErased.CrossTenant(
                    count, entityId, tenantIds, Instant.now(clock)));
        }
        return count;
    }

    @Override public String store(MemoryInput input) { return delegate.store(input); }
    @Override public StoreAllResult storeAll(List<MemoryInput> inputs) { return delegate.storeAll(inputs); }
    @Override public List<Memory> query(MemoryQuery query) { return delegate.query(query); }
    @Override public void eraseById(String memoryId, String entityId, String tenantId) { delegate.eraseById(memoryId, entityId, tenantId); }
    @Override public Set<MemoryCapability> capabilities() { return delegate.capabilities(); }
    @Override public List<Memory> scan(MemoryScanRequest request) { return delegate.scan(request); }
    @Override public int purge(MemoryRetentionPolicy policy) { return delegate.purge(policy); }
    @Override public Set<String> discoverTenants(String attributeKey, String attributeValue) { return delegate.discoverTenants(attributeKey, attributeValue); }
}
