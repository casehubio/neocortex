package io.casehub.neocortex.knowledge;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class KnowledgePipelineMetrics {

    private final MeterRegistry registry;

    public KnowledgePipelineMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordCacheHit(String queryType, String tenantId) {
        Counter.builder("knowledge.cache.hits")
            .tag("queryType", queryType)
            .tag("tenantId", tenantId)
            .register(registry).increment();
    }

    public void recordCacheMiss(String queryType, String tenantId) {
        Counter.builder("knowledge.cache.misses")
            .tag("queryType", queryType)
            .tag("tenantId", tenantId)
            .register(registry).increment();
    }

    public void recordResolutionDecision(String tier, String tenantId) {
        Counter.builder("knowledge.resolution.decisions")
            .tag("tier", tier)
            .tag("tenantId", tenantId)
            .register(registry).increment();
    }

    public Timer.Sample startProviderFetch() {
        return Timer.start(registry);
    }

    public void recordProviderFetch(Timer.Sample sample, String providerId, String tenantId) {
        sample.stop(Timer.builder("knowledge.provider.fetch.duration")
            .tag("providerId", providerId)
            .tag("tenantId", tenantId)
            .register(registry));
    }

    public void recordProviderError(String providerId, String tenantId) {
        Counter.builder("knowledge.provider.fetch.errors")
            .tag("providerId", providerId)
            .tag("tenantId", tenantId)
            .register(registry).increment();
    }

    public void recordEviction(int evicted, int skipped, String tenantId) {
        Counter.builder("knowledge.eviction.entities.removed")
            .tag("tenantId", tenantId)
            .register(registry).increment(evicted);
        Counter.builder("knowledge.eviction.entities.skipped.session")
            .tag("tenantId", tenantId)
            .register(registry).increment(skipped);
    }

    public void recordPromotion(boolean created, String tenantId) {
        Counter.builder("knowledge.promotion.operations")
            .tag("result", created ? "created" : "enriched")
            .tag("tenantId", tenantId)
            .register(registry).increment();
    }
}
