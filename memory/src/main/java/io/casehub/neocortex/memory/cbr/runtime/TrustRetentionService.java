package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.AgentTrustProvider;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrScanRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

@ApplicationScoped
public class TrustRetentionService {

    private static final Logger LOG = Logger.getLogger(TrustRetentionService.class);

    private final CbrCaseMemoryStore   store;
    private final AgentTrustProvider   trustProvider;
    private final TrustRetentionConfig config;

    @Inject
    TrustRetentionService(CbrCaseMemoryStore store,
                          Instance<AgentTrustProvider> trustProviderInstance,
                          TrustRetentionConfig config) {
        this(store,
             trustProviderInstance.isResolvable() ? trustProviderInstance.get() : null,
             config);
    }

    TrustRetentionService(CbrCaseMemoryStore store,
                          AgentTrustProvider trustProvider,
                          TrustRetentionConfig config) {
        this.store         = store;
        this.trustProvider = trustProvider;
        this.config        = config;
    }

    void evaluateTrajectories() {
        if (!config.enabled()) {return;}
        if (trustProvider == null) {
            LOG.info("Trust retention skipped — no AgentTrustProvider available");
            return;
        }

        MemoryDomain domain = new MemoryDomain(config.domain());
        Set<String>  tenants;
        try {
            tenants = store.discoverTenants(domain);
        } catch (UnsupportedOperationException e) {
            LOG.info("Trust retention skipped — store does not support discoverTenants");
            return;
        }

        for (String tenantId : tenants) {
            try {
                evaluateTenant(tenantId, domain);
            } catch (Exception e) {
                LOG.warnf("Trust retention failed for tenant %s: %s",
                          tenantId, e.getMessage());
            }
        }
    }

    private void evaluateTenant(String tenantId, MemoryDomain domain) {
        for (String caseType : config.caseTypes()) {
            Map<String, OptionalDouble> trustCache    = new HashMap<>();
            Map<String, Integer>        purgedByAgent = new HashMap<>();
            String                      cursor        = null;
            do {
                var req    = new CbrScanRequest(tenantId, domain, caseType, 500, cursor);
                var result = store.scan(req);
                for (var c : result.items()) {
                    if (c.producerAgentId() == null) {continue;}
                    OptionalDouble trust = trustCache.computeIfAbsent(
                            c.producerAgentId(),
                            agentId -> trustProvider.currentTrustScore(agentId));
                    if (trust.isEmpty()) {continue;}
                    if (trust.getAsDouble() >= config.minCurrentTrust()) {continue;}
                    store.erase(new EraseRequest(
                            c.entityId(), domain, tenantId, c.caseId()));
                    purgedByAgent.merge(c.producerAgentId(), 1, Integer::sum);
                }
                cursor = result.nextCursor();
            } while (cursor != null);

            for (var entry : purgedByAgent.entrySet()) {
                LOG.info("Trust retention purged " + entry.getValue()
                         + " cases from agent " + entry.getKey()
                         + " (tenant=" + tenantId + ", caseType=" + caseType + ")");
            }
        }}
}
