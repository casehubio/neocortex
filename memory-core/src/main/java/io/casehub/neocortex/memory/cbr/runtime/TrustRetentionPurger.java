package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.cbr.AgentTrustProvider;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.CbrScanRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.logging.Logger;

public class TrustRetentionPurger {

    private static final Logger LOG = Logger.getLogger(TrustRetentionPurger.class.getName());

    private final CbrRecordStore     store;
    private final AgentTrustProvider trustProvider;

    public TrustRetentionPurger(CbrRecordStore store, AgentTrustProvider trustProvider) {
        this.store = store;
        this.trustProvider = trustProvider;
    }

    public void evaluateTrajectories(boolean enabled, Optional<String> domain,
                                      Optional<List<String>> caseTypes,
                                      double minCurrentTrust) {
        if (!enabled) { return; }
        if (trustProvider == null) {
            LOG.info("Trust retention skipped — no AgentTrustProvider available");
            return;
        }

        String domainName = domain.orElseThrow(() -> new IllegalStateException(
                "casehub.cbr.trust-retention.domain must be set when casehub.cbr.trust-retention.enabled=true"));
        List<String> types = caseTypes.orElseThrow(() -> new IllegalStateException(
                "casehub.cbr.trust-retention.case-types must be set when casehub.cbr.trust-retention.enabled=true"));

        MemoryDomain memDomain = new MemoryDomain(domainName);
        Set<String> tenants;
        try {
            tenants = store.discoverTenants(memDomain);
        } catch (UnsupportedOperationException e) {
            LOG.info("Trust retention skipped — store does not support discoverTenants");
            return;
        }

        for (String tenantId : tenants) {
            try {
                evaluateTenant(tenantId, memDomain, types, minCurrentTrust);
            } catch (Exception e) {
                LOG.warning("Trust retention failed for tenant " + tenantId + ": " + e.getMessage());
            }
        }
    }

    private void evaluateTenant(String tenantId, MemoryDomain domain,
                                 List<String> caseTypes, double minCurrentTrust) {
        for (String caseType : caseTypes) {
            Map<String, OptionalDouble> trustCache = new HashMap<>();
            Map<String, Integer> purgedByAgent = new HashMap<>();
            String cursor = null;
            do {
                var req = new CbrScanRequest(tenantId, domain, caseType, 500, cursor);
                var result = store.scan(req);
                for (var c : result.items()) {
                    if (c.producerAgentId() == null) { continue; }
                    OptionalDouble trust = trustCache.computeIfAbsent(
                            c.producerAgentId(),
                            agentId -> trustProvider.currentTrustScore(agentId));
                    if (trust.isEmpty()) { continue; }
                    if (trust.getAsDouble() >= minCurrentTrust) { continue; }
                    store.erase(new EraseRequest(
                            Subject.of("unknown", c.entityId()), domain, tenantId, c.caseId()));
                    purgedByAgent.merge(c.producerAgentId(), 1, Integer::sum);
                }
                cursor = result.nextCursor();
            } while (cursor != null);

            for (var entry : purgedByAgent.entrySet()) {
                LOG.info("Trust retention purged " + entry.getValue()
                         + " cases from agent " + entry.getKey()
                         + " (tenant=" + tenantId + ", caseType=" + caseType + ")");
            }
        }
    }
}
