package io.casehub.neocortex.memory.runtime;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryCapability;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryRetentionPolicy;

import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

public class MemoryRetentionPurger {

    private static final Logger LOG = Logger.getLogger(MemoryRetentionPurger.class.getName());

    private final CaseMemoryStore store;

    public MemoryRetentionPurger(CaseMemoryStore store) {
        this.store = store;
    }

    public void purgeExpired(boolean enabled, Optional<String> domain,
                             Optional<Integer> maxAgeDays, Optional<Double> minConfidence) {
        if (!enabled) { return; }
        if (!store.capabilities().contains(MemoryCapability.DISCOVER_TENANTS)) {
            LOG.info("Memory retention skipped — store does not support DISCOVER_TENANTS");
            return;
        }
        if (!store.capabilities().contains(MemoryCapability.PURGE)) {
            LOG.info("Memory retention skipped — store does not support PURGE");
            return;
        }

        String domainName = domain.orElseThrow(() -> new IllegalStateException(
                "casehub.memory.retention.domain must be set when casehub.memory.retention.enabled=true"));

        MemoryDomain memDomain = new MemoryDomain(domainName);
        Set<String> tenants = store.discoverTenants(null, null);
        for (String tenantId : tenants) {
            try {
                MemoryRetentionPolicy policy = new MemoryRetentionPolicy(
                        tenantId, memDomain,
                        maxAgeDays.orElse(null),
                        minConfidence.orElse(null));
                int purged = store.purge(policy);
                if (purged > 0) {
                    LOG.info("Memory retention: purged " + purged + " memories for tenant " + tenantId);
                }
            } catch (Exception e) {
                LOG.warning("Memory retention failed for tenant " + tenantId + ": " + e.getMessage());
            }
        }
    }
}
