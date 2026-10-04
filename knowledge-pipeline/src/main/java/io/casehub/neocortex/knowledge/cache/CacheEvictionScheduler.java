package io.casehub.neocortex.knowledge.cache;

import io.casehub.neocortex.knowledge.ResearchState;
import io.casehub.neocortex.knowledge.SpatialCacheStore;
import io.casehub.neocortex.knowledge.dedup.DedupIndexStore;
import io.casehub.neocortex.knowledge.research.ResearchSessionStore;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CacheEvictionScheduler {

    private static final Logger LOG = Logger.getLogger(
        CacheEvictionScheduler.class.getName());

    private final SpatialCacheStore cacheStore;
    private final EntityMetadataStore metadataStore;
    private final ResearchSessionStore sessionStore;
    private final DedupIndexStore dedupStore;
    private final Duration maxEntityAge;

    public CacheEvictionScheduler(SpatialCacheStore cacheStore,
                                    EntityMetadataStore metadataStore,
                                    ResearchSessionStore sessionStore,
                                    DedupIndexStore dedupStore,
                                    Duration maxEntityAge) {
        this.cacheStore = cacheStore;
        this.metadataStore = metadataStore;
        this.sessionStore = sessionStore;
        this.dedupStore = dedupStore;
        this.maxEntityAge = maxEntityAge;
    }

    public void runEviction() {
        Set<String> tenants = discoverTenants();
        Instant now = Instant.now();

        for (String tenantId : tenants) {
            try {
                evictForTenant(tenantId, now);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Eviction failed for tenant " + tenantId, e);
            }
        }
    }

    private void evictForTenant(String tenantId, Instant now) {
        var expired = cacheStore.findExpired(tenantId, now);
        int evicted = 0;
        int skipped = 0;

        for (String entityId : expired) {
            if (isProtectedBySession(entityId)) {
                skipped++;
                continue;
            }
            cacheStore.remove(entityId, tenantId);
            metadataStore.delete(entityId);
            evicted++;
        }

        if (evicted > 0 || skipped > 0) {
            LOG.info("Eviction for tenant " + tenantId + ": evicted=" + evicted
                + " skipped=" + skipped);
        }
    }

    private boolean isProtectedBySession(String entityId) {
        Set<String> sessionIds = metadataStore.sessionsFor(entityId);
        for (String sessionId : sessionIds) {
            var session = sessionStore.get(sessionId);
            if (session.isPresent()) {
                ResearchState state = session.get().state();
                if (state == ResearchState.ACTIVE || state == ResearchState.PAUSED) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> discoverTenants() {
        return cacheStore.discoverTenants();
    }
}
