# Receive Memory Hygiene Implementation from Agency

**Issue:** casehubio/neocortex#1115
**Blocked by:** nothing on the neocortex side
**Blocks:** casehubio/agency#490 (adopt CognitiveLifecycle, delete dead types)
**Date:** 2026-11-18

## Summary

Receive the memory hygiene implementation from agency (`social/memory/`) into neocortex's `cognition` module. The existing `MemoryHygieneOrchestrator` SPI in `cognition-api` already declares the contract; this issue provides the implementation that fulfils it.

The hygiene system performs three functions during each cognitive tick:
1. **Retention scoring + eviction** — scores each memory's importance via weighted composite (confidence × recency × scope × trust), evicts memories below the retention threshold
2. **Consolidation** — groups similar surviving memories, summarises them into a single merged record, supersedes the originals
3. **Integrity checking** — detects orphaned supersessions and optionally escalates to semantic verification

A `MemoryHygieneScheduler` composes these with the existing reflection infrastructure into a full maintenance tick: hygiene → reflection → peer-linking → integrity.

## Scope

### In scope

- Core memory hygiene: orchestrator implementation, scorers, retention logic, value types
- Integrity checking: SPI + default implementation + semantic escalation SPI
- Consolidation summariser: new SPI to replace agency's `ContentSummariser`
- 5 test files adapted to neocortex infrastructure

### Out of scope

- **Evolution types** (7 files in `social/memory/evolution/`) — these implement agency-specific SPIs (`ImprovementProposalSource`, `RegressionEvaluator`, `CapabilityArea`, `ConflictStrategy`, `ImprovementCategoryProvider`). They are agency integration code, not cognitive residue. Memory hygiene parameter adaptation for standalone neocortex should be designed as a consolidation phase using existing CBR infrastructure — tracked as a separate follow-up issue.
- **Cognitive evolution types** (4 files in `social/cognition/evolution/`) — depend on agency's `TickTrace` and stigmergy model. Same rationale as above.
- **Reflection store types** (4 files) — replaced by existing neocortex `ReflectionOrchestrator` infrastructure.

## Architecture

### New types in cognition-api

All placed in `io.casehub.neocortex.cognition.memory` alongside the existing `MemoryHygieneOrchestrator` interface.

| Type | Kind | Purpose |
|------|------|---------|
| `ConfidenceScorer` | `@FunctionalInterface` | Scores memory importance given a `CbrMatch` and timestamp |
| `ConsolidationSummariser` | `@FunctionalInterface` | Merges multiple memory texts into a consolidated summary. NEW — replaces agency's `ContentSummariser` |
| `IntegrityChecker` | `@FunctionalInterface` | Detects store inconsistencies (orphaned supersessions, etc.) |
| `SemanticIntegrityChecker` | `@FunctionalInterface` | LLM-escalated integrity verification for flagged violations |
| `RetentionConfig` | record | Weighted scoring parameters: retentionThreshold, confidenceWeight, recencyWeight, scopeWeight, trustWeight. Validated in compact constructor. `DEFAULT` constant. |
| `RetentionScore` | record | Per-memory scored result with `compute()` static factory for weighted composite calculation |
| `HygieneEvent` | sealed interface | CDI-compatible events: `MemoryEvicted`, `MemoryConsolidated`, `ReflectionGenerated`, `IntegrityViolationDetected` |
| `HygieneTick` | sealed interface | Tick outcomes: `Idle(reason)`, `Completed(consolidated, evicted, totalScored, scores)`, `Failed(reason)` |
| `MaintenanceTick` | sealed interface | Full maintenance outcomes: `Completed(hygiene, reflectionsGenerated, crossLinksCreated, violations)`, `Failed(stage, reason)` |
| `IntegrityViolation` | record | `caseId`, `type`, `detail`, `escalateToSemantic` |
| `ViolationType` | enum | `ORPHANED_SUPERSESSION`, `DUPLICATE_CASE`, `MISSING_FEATURES`, `UNPROCESSED_STALE`, `SEMANTIC_CONFLICT` |

### New types in cognition

All placed in `io.casehub.neocortex.cognition.memory`.

| Type | Kind | Purpose |
|------|------|---------|
| `MemoryHygieneOrchestratorImpl` | `@ApplicationScoped` | Implements the existing `MemoryHygieneOrchestrator` SPI. Per-agent/tenant locking via `ConcurrentHashMap<String, ReentrantLock>`. Iterates case types, scores memories, evicts below threshold, consolidates similar survivors. |
| `MemoryHygieneScheduler` | class | Composes orchestrator + `ReflectionOrchestrator` + `IntegrityChecker` into a full maintenance tick. Uses neocortex's existing reflection infrastructure (not agency's `ReflectionStore`). |
| `ArousalScorer` | class | Implements both `ContentScorer` (from memory-api) and `ConfidenceScorer`. Keyword-based arousal detection from HIGH_AROUSAL word set. |
| `SurpriseScorer` | class | Implements both `ContentScorer` and `ConfidenceScorer`. Metadata-diversity-based surprise scoring. Static `toScoreableContent()` utility shared with `ArousalScorer`. |
| `CompositeConfidenceScorer` | class | Weighted composition of multiple `ConfidenceScorer` instances. |
| `WeightedScorer` | record | Pairs a `ConfidenceScorer` with a weight. |
| `DefaultIntegrityChecker` | class | Detects orphaned supersessions via `CbrRecordStore.findSupersededCases()`. Escalates flagged violations to `SemanticIntegrityChecker`. |
| `NoOpSemanticIntegrityChecker` | `@DefaultBean` | Returns empty list. Displaced by LLM-backed implementation when available. |
| `NoOpConsolidationSummariser` | `@DefaultBean` | Returns empty string. Displaced by LLM-backed or extractive summariser when available. |

### Key adaptations from agency code

1. **`KeyedLock`** → inline `ConcurrentHashMap<String, ReentrantLock>` pattern (agency utility, trivial to replace)
2. **`ContentSummariser<CbrMatch, SummaryResult>`** → `ConsolidationSummariser` SPI (`String summarise(List<String> texts)`). Simplified to pure text in/out — the agency type carries generic type parameters and CompletableFuture that are unnecessary for this use case.
3. **`ReflectionStore`** → `ReflectionOrchestrator.reflect()` (already in neocortex memory-api/memory-core). The scheduler calls `reflect()` and fires `ReflectionRecorded` CDI events through the existing pipeline rather than writing to a separate store.
4. **Package** → `io.casehub.neocortex.cognition.memory` for both API and implementation (matches existing `MemoryHygieneOrchestrator` SPI location in cognition-api).

### CDI wiring

`CognitionCoreFactory` already has `Instance<MemoryHygieneOrchestrator>` and wires it into `CognitionCore`. The new `MemoryHygieneOrchestratorImpl` will be `@ApplicationScoped` and activates when on classpath.

Default bean producers in `CognitionDefaultBeans`:
- `ConfidenceScorer` → `CompositeConfidenceScorer` with `ArousalScorer(weight=1.0)` + `SurpriseScorer(weight=0.5)`
- `IntegrityChecker` → `DefaultIntegrityChecker`
- `SemanticIntegrityChecker` → `NoOpSemanticIntegrityChecker`
- `ConsolidationSummariser` → `NoOpConsolidationSummariser`
- `RetentionConfig` → `RetentionConfig.DEFAULT`

### Existing MemoryHygieneOrchestrator SPI — no changes needed

```java
public interface MemoryHygieneOrchestrator {
    void tick(String agentId, String tenantId);
    KnowledgeGapSummary knowledgeGaps(String agentId, String tenantId);
}
```

The agency implementation returns `HygieneTick` from `tick()` but the SPI returns `void`. The implementation stores the tick result internally and exposes gap state via `knowledgeGaps()`. This is correct — the SPI consumer (CognitionCore) doesn't need the tick details; it just needs to trigger the tick and query gap state for prompt rendering.

### Dependencies

**cognition-api additions depend on:**
- `memory-api` (already a dependency) — for `CbrMatch`, `CbrRecord`, `MemoryDomain`, `EraseRequest`, `KnowledgeGapSummary`, `ContentScorer`, `ScoreableContent`

**cognition additions depend on:**
- `memory-api` — for `CbrRecordStore`, `SupersessionStatus`, `ReflectionOrchestrator`
- No new module dependencies required

### Tests

Adapt 5 test files from agency (drop 9 evolution + reflection store tests):

| Agency test | Neocortex test | Adaptations |
|-------------|---------------|-------------|
| `ConfidenceScorerTest` | `ConfidenceScorerTest` | Package rename only |
| `DefaultIntegrityCheckerTest` | `DefaultIntegrityCheckerTest` | Package rename only |
| `FoundationTypesTest` | `FoundationTypesTest` | Package rename, test RetentionConfig/RetentionScore/HygieneEvent/HygieneTick/MaintenanceTick |
| `MemoryHygieneOrchestratorTest` | `MemoryHygieneOrchestratorImplTest` | Replace ContentSummariser with ConsolidationSummariser, replace KeyedLock with inline locking |
| `MemoryHygieneSchedulerTest` | `MemoryHygieneSchedulerTest` | Replace ReflectionStore with ReflectionOrchestrator |

## Follow-up issues

1. **Memory hygiene parameter adaptation** — design a `HygieneAdaptationPhase` consolidation phase that uses CBR outcomes to tune hygiene parameters (retention threshold, confidence weights, consolidation timing). Neocortex-native replacement for agency's evolution types.
2. **LLM-backed ConsolidationSummariser** — provide a real summariser implementation so consolidation produces meaningful merged text rather than no-op.
3. **LLM-backed SemanticIntegrityChecker** — provide a real semantic checker that can verify flagged violations against memory content.

## References

- `cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/MemoryHygieneOrchestrator.java` — existing SPI
- `cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCore.java:311` — existing tick wiring
- `cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCoreFactory.java:49` — existing CDI injection
- `agency-core/src/main/java/io/casehub/agency/social/memory/` — source implementation
- `memory-api/src/main/java/io/casehub/neocortex/memory/reflection/ReflectionOrchestrator.java` — reused for reflection
- `memory-api/src/main/java/io/casehub/neocortex/memory/experience/ContentScorer.java` — dual-interface scorer pattern
- casehubio/agency#447 — parent migration issue
- casehubio/agency#490 — blocked downstream (adopt CognitiveLifecycle)
