package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrRetentionPolicy;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

public class CbrRetentionPurger {

    private static final Logger LOG = Logger.getLogger(CbrRetentionPurger.class.getName());

    private final CbrCaseMemoryStore store;

    public CbrRetentionPurger(CbrCaseMemoryStore store) {
        this.store = store;
    }

    public void purgeExpired(boolean enabled, Optional<String> domain,
                             Optional<List<String>> caseTypes,
                             Optional<Integer> maxAgeDays,
                             Optional<Integer> maxCasesPerType,
                             Optional<Double> minTrustScore) {
        if (!enabled) { return; }

        String domainName = domain.orElseThrow(() -> new IllegalStateException(
                "casehub.cbr.retention.domain must be set when casehub.cbr.retention.enabled=true"));
        List<String> types = caseTypes.orElseThrow(() -> new IllegalStateException(
                "casehub.cbr.retention.case-types must be set when casehub.cbr.retention.enabled=true"));

        MemoryDomain memDomain = new MemoryDomain(domainName);
        Set<String> tenants;
        try {
            tenants = store.discoverTenants(memDomain);
        } catch (UnsupportedOperationException e) {
            LOG.info("CBR retention skipped — store does not support discoverTenants");
            return;
        }

        for (String tenantId : tenants) {
            for (String caseType : types) {
                try {
                    CbrRetentionPolicy policy = new CbrRetentionPolicy(
                            tenantId, memDomain, caseType,
                            maxAgeDays.orElse(null),
                            maxCasesPerType.orElse(null),
                            minTrustScore.orElse(null));
                    store.purge(policy);
                } catch (Exception e) {
                    LOG.warning("CBR retention failed for tenant " + tenantId +
                                ", caseType " + caseType + ": " + e.getMessage());
                }
            }
        }
    }
}
