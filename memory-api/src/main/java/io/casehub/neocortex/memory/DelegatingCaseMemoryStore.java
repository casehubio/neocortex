package io.casehub.neocortex.memory;

import java.util.List;
import java.util.Set;

public abstract class DelegatingCaseMemoryStore implements CaseMemoryStore {

    protected final CaseMemoryStore delegate;

    protected DelegatingCaseMemoryStore(CaseMemoryStore delegate) {
        this.delegate = delegate;
    }

    @Override public String store(MemoryInput input) { return delegate.store(input); }
    @Override public StoreAllResult storeAll(List<MemoryInput> inputs) { return delegate.storeAll(inputs); }
    @Override public List<Memory> query(MemoryQuery query) { return delegate.query(query); }
    @Override public int erase(EraseRequest request) { return delegate.erase(request); }
    @Override public int eraseSubject(Subject subject, String tenantId) { return delegate.eraseSubject(subject, tenantId); }
    @Deprecated(forRemoval = true) @Override public int eraseEntity(String entityId, String tenantId) { return delegate.eraseEntity(entityId, tenantId); }
    @Override public void eraseById(String memoryId, Subject subject, String tenantId) { delegate.eraseById(memoryId, subject, tenantId); }
    @Deprecated(forRemoval = true) @Override public void eraseById(String memoryId, String entityId, String tenantId) { delegate.eraseById(memoryId, entityId, tenantId); }
    @Override public int eraseSubjectAcrossTenants(Subject subject, Set<String> tenantIds) { return delegate.eraseSubjectAcrossTenants(subject, tenantIds); }
    @Deprecated(forRemoval = true) @Override public int eraseEntityAcrossTenants(String entityId, Set<String> tenantIds) { return delegate.eraseEntityAcrossTenants(entityId, tenantIds); }
    @Override public Set<MemoryCapability> capabilities() { return delegate.capabilities(); }
    @Override public List<Memory> scan(MemoryScanRequest request) { return delegate.scan(request); }
    @Override public int purge(MemoryRetentionPolicy policy) { return delegate.purge(policy); }
    @Override public Set<String> discoverTenants(String attributeKey, String attributeValue) { return delegate.discoverTenants(attributeKey, attributeValue); }
}
