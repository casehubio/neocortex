package io.casehub.neocortex.cognition.personality;

import io.casehub.eidos.api.DispositionSignalStore;
import io.casehub.neocortex.cognitive.TenantAgentKey;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public final class DispositionSignalWriter {

    private static final int STRIPE_COUNT = 256;

    private final DispositionSignalStore store;
    private final IsolatedTransactions tx;
    private final ReentrantLock[] stripes;
    private final ConcurrentHashMap<TenantAgentKey, ConcurrentHashMap<String, AtomicLong>> pending =
            new ConcurrentHashMap<>();

    public DispositionSignalWriter(DispositionSignalStore store, IsolatedTransactions tx) {
        this.store = store;
        this.tx = tx;
        this.stripes = new ReentrantLock[STRIPE_COUNT];
        for (int i = 0; i < STRIPE_COUNT; i++) {
            stripes[i] = new ReentrantLock();
        }
    }

    public void enqueue(String tenantId, String agentId, List<String> terms) {
        var key = new TenantAgentKey(tenantId, agentId);
        var agentPending = pending.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        for (var term : terms) {
            agentPending.computeIfAbsent(term, t -> new AtomicLong()).incrementAndGet();
        }
        var lock = stripes[key.stripeIndex(STRIPE_COUNT)];
        if (lock.tryLock()) {
            try {
                flushPending(key);
            } finally {
                lock.unlock();
            }
        }
    }

    public void drain(String tenantId, String agentId) {
        var key = new TenantAgentKey(tenantId, agentId);
        var lock = stripes[key.stripeIndex(STRIPE_COUNT)];
        lock.lock();
        try {
            flushPending(key);
        } finally {
            lock.unlock();
        }
    }

    public void decay(String tenantId, String agentId, double factor) {
        var key = new TenantAgentKey(tenantId, agentId);
        var lock = stripes[key.stripeIndex(STRIPE_COUNT)];
        lock.lock();
        try {
            flushPending(key);
            tx.requiresNew(() -> store.decay(agentId, tenantId, factor));
        } finally {
            lock.unlock();
        }
    }

    public void clear(String tenantId, String agentId) {
        var key = new TenantAgentKey(tenantId, agentId);
        var lock = stripes[key.stripeIndex(STRIPE_COUNT)];
        lock.lock();
        try {
            var agentPending = pending.get(key);
            if (agentPending != null) {
                for (var entry : agentPending.entrySet()) {
                    entry.getValue().getAndSet(0);
                }
            }
            tx.requiresNew(() -> store.clear(agentId, tenantId));
        } finally {
            lock.unlock();
        }
    }

    public void flushAll() {
        for (var key : pending.keySet()) {
            var lock = stripes[key.stripeIndex(STRIPE_COUNT)];
            lock.lock();
            try {
                flushPending(key);
            } finally {
                lock.unlock();
            }
        }
    }

    private void flushPending(TenantAgentKey key) {
        var agentPending = pending.get(key);
        if (agentPending == null || agentPending.isEmpty()) {return;}
        var taken = new java.util.LinkedHashMap<String, Long>();
        for (var entry : agentPending.entrySet()) {
            long count = entry.getValue().getAndSet(0);
            if (count > 0) {taken.put(entry.getKey(), count);}
        }
        if (taken.isEmpty()) {return;}
        try {
            tx.requiresNew(() -> {
                for (var entry : taken.entrySet()) {
                    for (long i = 0; i < entry.getValue(); i++) {
                        store.recordActivation(key.agentId(), key.tenantId(), entry.getKey());
                    }
                }
            });
        } catch (RuntimeException e) {
            for (var entry : taken.entrySet()) {
                agentPending.computeIfAbsent(entry.getKey(), k -> new AtomicLong()).addAndGet(entry.getValue());
            }
            throw e;
        }
    }
}
