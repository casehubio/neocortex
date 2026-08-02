package io.casehub.neocortex.memory.runtime;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryCapability;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryRetentionPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Set;

@ApplicationScoped
public class MemoryRetentionScheduler {

    private static final Logger LOG = Logger.getLogger(MemoryRetentionScheduler.class);

    private final CaseMemoryStore store;
    private final MemoryRetentionConfig config;

    @Inject
    MemoryRetentionScheduler(CaseMemoryStore store, MemoryRetentionConfig config) {
        this.store = store;
        this.config = config;
    }

    void purgeExpired() {
        if (!config.enabled()) return;
        if (!store.capabilities().contains(MemoryCapability.DISCOVER_TENANTS)) {
            LOG.info("Memory retention skipped — store does not support DISCOVER_TENANTS");
            return;
        }
        if (!store.capabilities().contains(MemoryCapability.PURGE)) {
            LOG.info("Memory retention skipped — store does not support PURGE");
            return;
        }

        MemoryDomain domain = new MemoryDomain(config.domain());
        Set<String> tenants = store.discoverTenants(null, null);
        for (String tenantId : tenants) {
            try {
                MemoryRetentionPolicy policy = new MemoryRetentionPolicy(
                        tenantId, domain,
                        config.maxAgeDays().orElse(null),
                        config.minImportance().orElse(null));
                int purged = store.purge(policy);
                if (purged > 0) {
                    LOG.infof("Memory retention: purged %d memories for tenant %s", purged, tenantId);
                }
            } catch (Exception e) {
                LOG.warnf("Memory retention failed for tenant %s: %s", tenantId, e.getMessage());
            }
        }
    }
}
