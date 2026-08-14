package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrRetentionPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

@ApplicationScoped
public class CbrRetentionScheduler {

    private static final org.jboss.logging.Logger LOG =
            org.jboss.logging.Logger.getLogger(CbrRetentionScheduler.class);

    private final CbrCaseMemoryStore store;
    private final CbrRetentionConfig config;

    @Inject
    CbrRetentionScheduler(CbrCaseMemoryStore store, CbrRetentionConfig config) {
        this.store  = store;
        this.config = config;
    }

    void purgeExpired() {
        if (!config.enabled()) {return;}

        if (config.domain().isBlank()) {
            throw new IllegalStateException(
                    "casehub.cbr.retention.domain must be set when casehub.cbr.retention.enabled=true");
        }
        if (config.caseTypes().isEmpty()) {
            throw new IllegalStateException(
                    "casehub.cbr.retention.case-types must be set when casehub.cbr.retention.enabled=true");
        }

        MemoryDomain domain = new MemoryDomain(config.domain());
        Set<String>  tenants;
        try {
            tenants = store.discoverTenants(domain);
        } catch (UnsupportedOperationException e) {
            LOG.info("CBR retention skipped — store does not support discoverTenants");
            return;
        }

        for (String tenantId : tenants) {
            for (String caseType : config.caseTypes()) {
                try {
                    CbrRetentionPolicy policy = new CbrRetentionPolicy(
                            tenantId, domain, caseType,
                            config.maxAgeDays().orElse(null),
                            config.maxCasesPerType().orElse(null),
                            config.minTrustScore().orElse(null));
                    store.purge(policy);
                } catch (Exception e) {
                    LOG.warnf("CBR retention failed for tenant %s, caseType %s: %s",
                              tenantId, caseType, e.getMessage());
                }
            }
        }
    }
}
