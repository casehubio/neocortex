package io.casehub.neocortex.entity.token.cdi;

import io.casehub.neocortex.entity.token.EntityIdentity;
import io.casehub.neocortex.entity.token.SurfaceForm;
import io.casehub.neocortex.entity.token.SurfaceFormTrie;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SurfaceFormTrieManager {

    static final long DEBOUNCE_MS = 200;

    private final EntityIdentityCache identityCache;
    private final ConcurrentHashMap<String, SurfaceFormTrie> tries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingRebuilds = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    public SurfaceFormTrieManager(EntityIdentityCache identityCache) {
        this.identityCache = identityCache;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "entity-trie-rebuild");
            t.setDaemon(true);
            return t;
        });
    }

    public SurfaceFormTrie getTrieForTenant(String tenantId) {
        return tries.computeIfAbsent(tenantId, this::buildTrie);
    }

    public void scheduleRebuild(String tenantId) {
        ScheduledFuture<?> existing = pendingRebuilds.get(tenantId);
        if (existing != null) {
            existing.cancel(false);
        }
        ScheduledFuture<?> future = scheduler.schedule(
            () -> rebuildNow(tenantId), DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        pendingRebuilds.put(tenantId, future);
    }

    public void rebuildNow(String tenantId) {
        pendingRebuilds.remove(tenantId);
        identityCache.invalidateTenant(tenantId);
        tries.put(tenantId, buildTrie(tenantId));
    }

    public void invalidateTenant(String tenantId) {
        tries.remove(tenantId);
        identityCache.invalidateTenant(tenantId);
    }

    private SurfaceFormTrie buildTrie(String tenantId) {
        Map<String, EntityIdentity> identities = identityCache.getAllIdentities(tenantId);
        List<SurfaceForm> allForms = new ArrayList<>();
        for (EntityIdentity identity : identities.values()) {
            allForms.addAll(identity.surfaceForms());
        }
        return SurfaceFormTrie.build(allForms);
    }

    void shutdown() {
        scheduler.shutdownNow();
    }
}
