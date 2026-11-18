# Memory Hygiene Parameter Adaptation via Consolidation Phase

**Issue:** casehubio/neocortex#1119
**Date:** 2026-11-18
**Status:** Draft

## Problem

Memory hygiene (`MemoryHygieneOrchestratorImpl`) uses a fixed `RetentionConfig` to score, evict, and consolidate memories. The five parameters (retentionThreshold + 4 weights) are set at construction and never change. When retention behavior is too aggressive (evicting valuable memories) or too lenient (accumulating noise), there is no feedback mechanism to adapt.

Agency's evolution types (improvement proposals, regression evaluation) solved this but depend on agency's stigmergy framework — they are agency integration code, not cognitive machinery. Neocortex needs a native replacement: a consolidation phase that observes hygiene quality and adjusts retention parameters.

## Design

### Architecture Overview

Two independent tick loops remain decoupled:

```
ConsolidationScheduler (5 min)        MemoryHygieneScheduler (external)
    │                                      │
    ├─ ... 27 existing phases ...          ├─ MemoryHygieneOrchestratorImpl
    ├─ @Priority(48)                       │   reads RetentionConfigSupplier.current()
    │   HygieneAdaptationPhase             │   scores, evicts, consolidates
    │   reads tenantGapSummary()  ◄────────┤   writes KnowledgeGapSummary
    │   computes new threshold             │
    │   writes AdaptiveRetentionConfig ────►│
    └─ ...                                 └─
```

The adaptation phase runs in the consolidation scheduler, reads quality signals from the hygiene orchestrator (produced during the *previous* hygiene tick), computes adjusted parameters, and writes them to a shared holder for the *next* hygiene tick to consume. The one-tick lag is acceptable because retention parameters are slow-moving.

### New Types

#### `RetentionConfigSupplier` (cognition-api)

```java
@FunctionalInterface
public interface RetentionConfigSupplier {
    RetentionConfig current();
}
```

Pure functional interface. No CDI annotations. The hygiene orchestrator injects this instead of holding a fixed `RetentionConfig` field.

#### `AdaptiveRetentionConfig` (cognition)

```java
@ApplicationScoped
public class AdaptiveRetentionConfig implements RetentionConfigSupplier {
    private final AtomicReference<RetentionConfig> config =
        new AtomicReference<>(RetentionConfig.DEFAULT);

    @Override
    public RetentionConfig current() {
        return config.get();
    }

    public void update(RetentionConfig newConfig) {
        config.set(newConfig);
    }
}
```

Thread-safe volatile reference swap. The adaptation phase calls `update()`; the hygiene orchestrator calls `current()`. No persistence — starts with `DEFAULT` on restart.

#### `HygieneAdaptationConfig` (cognition)

```java
@ConfigMapping(prefix = "casehub.cognition.hygiene-adaptation")
public interface HygieneAdaptationConfig {
    @WithDefault("false")
    boolean enabled();

    @WithDefault("0.3")
    double evictionPressureUpperThreshold();

    @WithDefault("0.05")
    double evictionPressureLowerThreshold();

    @WithDefault("0.02")
    double stepSize();

    @WithDefault("0.01")
    double minRetentionThreshold();

    @WithDefault("0.5")
    double maxRetentionThreshold();
}
```

#### `HygieneAdaptationEvent` (cognition)

```java
public record HygieneAdaptationEvent(
    String tenantId,
    RetentionConfig previousConfig,
    RetentionConfig newConfig,
    double evictionPressure,
    AdaptationDirection direction,
    Instant timestamp
) {
    public enum AdaptationDirection { LOWERED, RAISED, HELD }
}
```

CDI event fired after each adaptation tick. Records previous/new config and the quality signal that drove the change.

### Modified Types

#### `MemoryHygieneOrchestrator` SPI (cognition-api)

Add tenant-level gap aggregation:

```java
KnowledgeGapSummary tenantGapSummary(String tenantId);
```

Aggregates across all agents for the tenant (sum lowRetentionCount, sum consolidationGroups, sum totalScored). The existing per-agent `knowledgeGaps(agentId, tenantId)` remains unchanged.

#### `MemoryHygieneOrchestratorImpl` (cognition)

Two changes:
1. Replace `private final RetentionConfig retentionConfig` with `private final RetentionConfigSupplier configSupplier`. Read `configSupplier.current()` at the start of each tick instead of using a fixed field.
2. Implement `tenantGapSummary(String tenantId)` by iterating `gapStates` entries matching the tenant suffix.

#### `CognitionDefaultBeans` (cognition)

Add a `@DefaultBean` producer for `RetentionConfigSupplier` that returns `RetentionConfig.DEFAULT` via a lambda. When `AdaptiveRetentionConfig` is on the classpath and enabled, CDI prefers the `@ApplicationScoped` bean over the `@DefaultBean`. This ensures the hygiene orchestrator always has a supplier — even when adaptation is disabled.

### HygieneAdaptationPhase (cognition)

```java
@ApplicationScoped
@Priority(48)
@IfBuildProperty(name = "casehub.cognition.hygiene-adaptation.enabled",
                 stringValue = "true", enableIfMissing = false)
public class HygieneAdaptationPhase implements ConsolidationPhase {

    private final MemoryHygieneOrchestrator hygieneOrchestrator;
    private final AdaptiveRetentionConfig adaptiveConfig;
    private final HygieneAdaptationConfig config;
    private final Event<HygieneAdaptationEvent> adaptationEvent;

    // Direction tracking — prevents oscillation
    private volatile KnowledgeGapSummary previousSummary;
    private volatile RetentionConfig previousConfig;
}
```

**Algorithm (per tenant per tick):**

1. Read `tenantGapSummary(tenantId)` from the hygiene orchestrator
2. If no previous summary exists (first tick), store current and return — no adaptation on first observation
3. Compute eviction pressure: `lowRetentionCount / totalScored` (guard against zero totalScored)
4. Compare with previous tick's pressure to determine direction:
   - If previous adjustment improved quality (pressure moved toward target range): **hold** — the adjustment is working
   - If pressure still above upper threshold (too many evictions): **lower** retentionThreshold by step
   - If pressure still below lower threshold (too few evictions): **raise** retentionThreshold by step
   - If pressure within target range: **hold** — system is healthy
5. Clamp new threshold to [minRetentionThreshold, maxRetentionThreshold]
6. If threshold changed: `adaptiveConfig.update(newConfig)`, fire `HygieneAdaptationEvent`
7. Store current summary and config as previous for next tick

**Priority 48 rationale:** The phase reads hygiene data from the *previous* hygiene tick, not from other consolidation phases. It is ordering-independent within the consolidation pipeline. Priority 48 runs it after SituationalDeformationPhase (46) and before DispositionEvolutionPhase (50), keeping it in the established 10-50 range without collision.

### Testing

- **Unit test for adaptation logic:** Given a KnowledgeGapSummary with high eviction pressure → threshold lowered. Given low pressure → threshold raised. Given improving trend → held. Given threshold at bounds → clamped.
- **Unit test for oscillation prevention:** Simulate alternating high/low pressure → verify threshold doesn't oscillate.
- **Unit test for tenant-level aggregation:** Multiple agents, one with poor retention → verify aggregate reflects combined state.
- **Integration test:** Wire phase into consolidation scheduler, verify it reads from hygiene orchestrator and updates AdaptiveRetentionConfig.

### Scope Boundaries

**In scope:**
- RetentionConfigSupplier SPI in cognition-api
- AdaptiveRetentionConfig holder in cognition
- HygieneAdaptationPhase in cognition
- HygieneAdaptationConfig with @IfBuildProperty gate
- HygieneAdaptationEvent for observability
- MemoryHygieneOrchestrator.tenantGapSummary() addition
- MemoryHygieneOrchestratorImpl modification to use RetentionConfigSupplier
- CognitionDefaultBeans producer for RetentionConfigSupplier (returns AdaptiveRetentionConfig when present, DEFAULT-returning lambda otherwise)

**Out of scope (future work):**
- CBR persistence of learned parameters (follow-on issue)
- Per-agent RetentionConfig adaptation
- Weight parameter (confidenceWeight, recencyWeight, etc.) adaptation — these are personality-driven
- Consolidation activity signal driving its own parameter (e.g. similarity thresholds)

## References

- casehubio/neocortex#1119 — parent issue
- casehubio/neocortex#1115 — migration that established the boundary
- casehubio/agency#447 — agency migration (excluded evolution types)
- MemoryHygieneOrchestratorImpl (cognition) — the orchestrator whose parameters adapt
- RetentionConfig (cognition-api) — the config record
- KnowledgeGapSummary (memory-api) — quality signal record
- BehavioralSynthesisPhase (mindmap-intelligence) — CBR outcome pattern template
- ConsolidationPhase SPI (mindmap-intelligence) — phase contract
- ConsolidationScheduler (mindmap-intelligence) — scheduler mechanics
- Decision review: /Users/mdproctor/reviews/casehub-neocortex/issue-1119-decision-20261118-152122/responses/reviewer-1.md
