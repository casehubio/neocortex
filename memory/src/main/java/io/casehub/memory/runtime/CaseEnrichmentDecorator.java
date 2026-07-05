package io.casehub.memory.runtime;

import io.casehub.neocortex.memory.*;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Decorator
@Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION)
public class CaseEnrichmentDecorator implements CaseMemoryStore {

    private static final Logger LOG = Logger.getLogger(CaseEnrichmentDecorator.class.getName());

    private final CaseMemoryStore delegate;
    private final List<CaseEnrichmentStep> sortedSteps;

    @Inject
    CaseEnrichmentDecorator(@Delegate @Any CaseMemoryStore delegate,
                            Instance<CaseEnrichmentStep> steps) {
        this.delegate = delegate;
        this.sortedSteps = steps.stream()
            .sorted(Comparator.comparingInt(CaseEnrichmentStep::priority))
            .toList();
    }

    CaseEnrichmentDecorator(CaseMemoryStore delegate, List<CaseEnrichmentStep> sortedSteps) {
        this.delegate = delegate;
        this.sortedSteps = sortedSteps.stream()
            .sorted(Comparator.comparingInt(CaseEnrichmentStep::priority))
            .toList();
    }

    @Override
    public String store(MemoryInput input) {
        return delegate.store(applyEnrichment(input));
    }

    @Override
    public StoreAllResult storeAll(List<MemoryInput> inputs) {
        return delegate.storeAll(inputs.stream().map(this::applyEnrichment).toList());
    }

    @Override public List<Memory> query(MemoryQuery q) { return delegate.query(q); }
    @Override public int erase(EraseRequest r) { return delegate.erase(r); }
    @Override public int eraseEntity(String entityId, String tenantId) { return delegate.eraseEntity(entityId, tenantId); }
    @Override public void eraseById(String memoryId, String entityId, String tenantId) { delegate.eraseById(memoryId, entityId, tenantId); }
    @Override public int eraseEntityAcrossTenants(String entityId, Set<String> tenantIds) { return delegate.eraseEntityAcrossTenants(entityId, tenantIds); }
    @Override public Set<MemoryCapability> capabilities() { return delegate.capabilities(); }
    @Override public void requireCapability(MemoryCapability cap) { delegate.requireCapability(cap); }
    @Override public List<Memory> scan(MemoryScanRequest request) { return delegate.scan(request); }
    @Override public Set<String> discoverTenants(String attributeKey, String attributeValue) { return delegate.discoverTenants(attributeKey, attributeValue); }

    private MemoryInput applyEnrichment(MemoryInput input) {
        if (sortedSteps.isEmpty()) return input;
        MemoryInput result = input;
        for (CaseEnrichmentStep step : sortedSteps) {
            if (step.appliesTo(result)) {
                try {
                    result = step.enrich(result);
                } catch (Exception e) {
                    if (step.required()) {
                        if (e instanceof RuntimeException re) throw re;
                        throw new RuntimeException("Required enrichment step failed: " + step.getClass().getName(), e);
                    }
                    LOG.log(Level.WARNING, "Enrichment step " + step.getClass().getName() + " failed", e);
                }
            }
        }
        return result;
    }
}
