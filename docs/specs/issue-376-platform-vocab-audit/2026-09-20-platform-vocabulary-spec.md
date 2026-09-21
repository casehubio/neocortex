# Platform Vocabulary Spec — Case vs Cbr

**Issue:** casehubio/neocortex#376
**Date:** 2026-09-20
**Status:** Design (no code until approved)
**Blocks:** #375 (rename implementation), slot 199 (parked, scope redefined)

---

## 1. Problem

Naming decisions across the platform were made piecemeal. The CBR subsystem in neocortex uses "Case" (`CbrCase`, `CbrCaseMemoryStore`) which collides with the engine's "Case" (`CaseInstance`, `CaseMetaModel`). An under-explored rename in #304 (`PlanCbrCase → ResolvedCase`, `TextualCbrCase → ResolutionGuide`) was incomplete — `FeatureVectorCbrCase` wasn't renamed, `CBR_TYPE` discriminators weren't updated, no `@Deprecated` bridges were provided. The rename broke downstream apps (life).

The result: three vocabularies coexist (CBR-paradigm names, Resolution names, unchanged names) with no consistency.

## 2. Design Principles

1. **Two nouns, not one.** The platform has two first-class nouns: `Case` (the thing being worked on) and `Cbr` (the subsystem that learns from past cases). Both are nouns — neither is a prefix on the other.

2. **Name things for what they ARE.** Concrete, self-descriptive names. `CbrRecordStore` not `CaseBase`. `CbrRecord` not `Exemplar`. Following the Drools v2 philosophy: `RuleUnit`/`DataStore`, not `KnowledgeBase`/`WorkingMemory`.

3. **Engine is frozen.** `CaseInstance`, `PlanItem`, `SubCaseGroup` — settled by prior refactor. Not touched.

4. **Pre-release: get it right once.** No half measures. All CBR types rename consistently. Cost is accepted.

## 3. The Two Nouns

| Noun | What it names | Subsystem | Types follow `Noun[Role]` |
|------|--------------|-----------|--------------------------|
| **Case** | The thing being worked on — the engine's domain concept | Engine, Memory, RAG | `CaseInstance`, `CaseMemoryStore`, `CaseRetriever` |
| **Cbr** | The subsystem that learns from past cases — case-based reasoning | CBR (neocortex memory) | `CbrRecord`, `CbrRecordStore`, `CbrMatch` |

The relationship: a `CaseInstance` (engine) executes → produces `CbrRecord`s (neocortex) → future cases retrieve similar records via `CbrRecordStore` to inform decisions.

`Cbr` is not an abbreviation grudgingly accepted for disambiguation. It IS the name of the reasoning subsystem — the same way `Rule` is the noun in Drools' `RuleUnit`. The CBR subsystem isn't a footnote on the Case subsystem — it's a peer.

## 4. Vocabulary Table

### 4.1 Data Types

| Current | New | CBR_TYPE | Description |
|---------|-----|----------|-------------|
| `CbrCase` | `CbrRecord` | (base interface) | A recorded past case — problem, solution, outcome, features |
| `ResolvedCase` (was `PlanCbrCase`) | `CbrPlanRecord` | `"plan"` | Records a past case's plan execution steps |
| `ResolutionGuide` (was `TextualCbrCase`) | `CbrGuidanceRecord` | `"textual"` | Records prose guidance from a past case |
| `FeatureVectorCbrCase` | `CbrFeatureRecord` | `"feature-vector"` | Records structured features for similarity matching |
| `ResolutionStep` (was `PlanTrace`) | `CbrPlanStep` | — | A step within a plan record |
| `GuidanceStep` | `CbrGuidanceStep` | — | A step within a guidance record |
| `ScoredCbrCase<T>` | `CbrMatch<T>` | — | A scored result from similarity retrieval |
| `CbrCase.cbrType()` | `CbrRecord.recordType()` | — | Discriminator accessor |

`CBR_TYPE` discriminator VALUES stay unchanged (`"plan"`, `"textual"`, `"feature-vector"`) — no data migration needed.

### 4.2 Store / Infrastructure

| Current | New | Role |
|---------|-----|------|
| `CbrCaseMemoryStore` | `CbrRecordStore` | Composite SPI — stores and retrieves CBR records |
| `CbrCaseStore` | `CbrRecordOps` | ISP: store operations |
| `CbrCaseRetriever` | `CbrRecordRetrieval` | ISP: similarity retrieval |
| `CbrCaseLifecycle` | `CbrRecordLifecycle` | ISP: supersede, reinstate, erase |
| `CbrCaseAdmin` | `CbrRecordAdmin` | ISP: schema registration, reconciliation |
| `DelegatingCbrCaseMemoryStore` | `DelegatingCbrRecordStore` | Forwarding base class for decorators |

The composite SPI stays as ONE SPI (not split into separate T-Box/A-Box SPIs). ISP sub-interfaces provide the separation for consumers who need it. Nobody injects them individually today.

### 4.3 Decorators / Implementations

| Current | New |
|---------|-----|
| `OutcomeWeightingCbrCaseMemoryStore` | `OutcomeWeightingCbrRecordStore` |
| `DiversityCbrCaseMemoryStore` | `DiversityCbrRecordStore` |
| `TrustWeightedCbrCaseMemoryStore` | `TrustWeightedCbrRecordStore` |
| `ScopeDecayCbrCaseMemoryStore` | `ScopeDecayCbrRecordStore` |
| `TrendEnrichmentCbrCaseMemoryStore` | `TrendEnrichmentCbrRecordStore` |
| `TemporalDecayCbrCaseMemoryStore` | `TemporalDecayCbrRecordStore` |
| `SupersessionNotificationCbrCaseMemoryStore` | `SupersessionNotificationCbrRecordStore` |
| `ErasureNotificationCbrCaseMemoryStore` | `ErasureNotificationCbrRecordStore` |
| `TrackingCbrCaseMemoryStore` | `TrackingCbrRecordStore` |
| `InMemoryCbrCaseMemoryStore` | `InMemoryCbrRecordStore` |
| `QdrantCbrCaseMemoryStore` | `QdrantCbrRecordStore` |
| `JpaCbrCaseMemoryStore` | `JpaCbrRecordStore` |
| `NoOpCbrCaseMemoryStore` | `NoOpCbrRecordStore` |
| `DiversityCbrCaseMemoryStoreCdiDecorator` | `DiversityCbrRecordStoreCdiDecorator` |
| `BridgedCbrStore` | `BridgedCbrStore` (unchanged — marker interface) |

### 4.4 CDI Events

| Current | New |
|---------|-----|
| `CbrCasesErased` | `CbrRecordErased` (singular) |
| `CbrCasesSuperseded` | `CbrRecordSuperseded` |
| `CbrCasesReinstated` | `CbrRecordReinstated` |
| `CbrRetrievalRecorded` | `CbrRetrievalRecorded` (unchanged) |
| `CbrRetrievalTrace` | `CbrRetrievalTrace` (unchanged) |
| `CbrAdaptationRecorded` | `CbrAdaptationRecorded` (unchanged) |
| `CbrEnsembleRecorded` | `CbrEnsembleRecorded` (unchanged) |

### 4.5 Adaptation Types

| Current | New | Rationale |
|---------|-----|-----------|
| `PlanAdapter` | `CbrPlanAdapter` | Bridges CbrRecord → engine Plan |
| `AdaptedPlan` | `AdaptedPlan` (unchanged) | Engine-side output |
| `AdaptedStep` | `AdaptedStep` (unchanged) | Engine vocabulary — correct coupling |
| `AdaptationAction` | `AdaptationAction` (unchanged) | — |
| `AdaptationTrace` | `CbrAdaptationTrace` | CBR audit trail |
| `PlanEnsembleAnalyzer` | `CbrPlanEnsembleAnalyzer` | Plan-specific ensemble analysis |
| `EnsemblePlan` | `EnsemblePlan` (unchanged) | Engine-side output |

### 4.6 Query / Config Types

| Current | New |
|---------|-----|
| `CbrQuery` | `CbrQuery` (unchanged) |
| `CbrFeatureSchema` | `CbrRecordSchema` |
| `CbrFeatureValidator` | `CbrRecordValidator` |
| `CbrSimilarityScorer` | `CbrSimilarityScorer` (unchanged) |
| `CbrFilter` | `CbrFilter` (unchanged) |
| `CbrOutcome` | `CbrOutcome` (unchanged) |
| `CbrRetentionPolicy` | `CbrRetentionPolicy` (unchanged) |
| `CbrCaseSummary` | `CbrRecordSummary` |

### 4.7 Testing Types

| Current | New |
|---------|-----|
| `CbrCaseMemoryStoreContractTest` | `CbrRecordStoreContractTest` |
| `CbrRetrievalTrackerContractTest` | `CbrRetrievalTrackerContractTest` (unchanged) |
| `PlanEnsembleAnalyzerContractTest` | `CbrPlanEnsembleAnalyzerContractTest` |
| `InMemoryCbrRetrievalTracker` | `InMemoryCbrRetrievalTracker` (unchanged) |

### 4.8 Tracking Types

| Current | New |
|---------|-----|
| `CbrRetrievalTracker` | `CbrRetrievalTracker` (unchanged) |
| `SqliteCbrRetrievalTracker` | `SqliteCbrRetrievalTracker` (unchanged) |
| `TrackingPlanAdapter` | `TrackingCbrPlanAdapter` |
| `TrackingPlanEnsembleAnalyzer` | `TrackingCbrPlanEnsembleAnalyzer` |

### 4.9 Frozen (no change)

- **Engine:** `CaseInstance`, `CaseMetaModel`, `SubCaseGroup`, `PlanItem`, `PlanItemRecord`, `TaskStatus`, `TaskDescriptor`, `TaskSnapshot`
- **Memory:** `CaseMemoryStore`, `GraphCaseMemoryStore`, `CaseEnrichmentStep`, `CaseEnrichmentDecorator`, `DelegatingCaseMemoryStore`
- **RAG:** `CaseRetriever`, `CaseContextRetriever`, `EmbeddingIngestor`
- **Cognitive:** `Confidence`, `ConfidenceOrigin`, `TemporalMark`, `MoodState`
- **MindMap:** `MindMapStore`, `MindMapNode`, `MindMapEdge`
- **Thing:** `Thing` interface
- **Config properties:** `casehub.cbr.*` — unchanged, consonant with Cbr noun
- **Package:** `io.casehub.neocortex.memory.cbr` — unchanged, consonant with Cbr noun

## 5. Downstream Impact

### 5.1 Codebase audit results

| Repo | Files affected | Custom `implements CbrCase` | Key types used |
|------|:-:|:-:|---|
| neocortex | 42 | 0 (defines SPI) | All types |
| engine | 6 | 0 | CbrRecordStore, CbrPlanRecord, CbrGuidanceRecord, CbrPlanAdapter |
| blocks | 24 | 0 | CbrRecordStore, CbrFeatureRecord, CbrMatch |
| aml | 24 | 0 | CbrRecordStore, CbrFeatureRecord, CbrRecordSchema |
| clinical | 40 | 0 | CbrRecordStore, CbrFeatureRecord, CbrPlanRecord, CbrPlanAdapter |
| quarkmind | 18 | 2 | SC2GameCbrCase → SC2GameCbrRecord, SC2AdvisoryCbrCase → SC2AdvisoryCbrRecord |
| iot | 4 | 1 | PlanCbrCase → PlanCbrRecord |
| platform | 1 | 0 | Javadoc reference only |
| life, soc, connectors | 0 | 0 | No CBR references |

**Total: 159 files across 8 repos.**

### 5.2 Custom implementations

Three classes outside neocortex implement `CbrCase` directly. All rename in the same coordinated pass:

| Repo | Current | New |
|------|---------|-----|
| iot | `PlanCbrCase implements CbrCase` | `PlanCbrRecord implements CbrRecord` |
| quarkmind | `SC2GameCbrCase implements CbrCase` | `SC2GameCbrRecord implements CbrRecord` |
| quarkmind | `SC2AdvisoryCbrCase implements CbrCase` | `SC2AdvisoryCbrRecord implements CbrRecord` |

## 6. Migration Strategy

### Phase 1: Introduce new interfaces with @Deprecated bridges

```java
public interface CbrRecord {
    String recordType();
    String problem();
    String solution();
    String outcome();
    Confidence confidence();
    // ...
}

@Deprecated
public interface CbrCase extends CbrRecord {
    @Deprecated
    default String cbrType() { return recordType(); }
}
```

Records implement `CbrCase` (which IS-A `CbrRecord`). Both old and new consumers satisfied. Same pattern for `CbrRecordStore` / `CbrCaseMemoryStore`.

### Phase 2: Rename concrete types

All concrete records and implementations rename. Construction sites change:
- `new ResolvedCase(...)` → `new CbrPlanRecord(...)`
- `new ResolutionGuide(...)` → `new CbrGuidanceRecord(...)`
- `new FeatureVectorCbrCase(...)` → `new CbrFeatureRecord(...)`

### Phase 3: Downstream migration

Each downstream repo updates imports and construction sites. Bridge interfaces ensure compilation during migration window.

### Phase 4: Remove bridges

After all consumers have migrated, remove `@Deprecated` interfaces.

## 7. What This Spec Does NOT Cover

- **String → typed identity** (`tenantId`, `caseType`, `producerAgentId`) — separate issue
- **Engine Case concept rename** — engine is frozen (D3)
- **Module restructuring** — no module splits or merges
- **Data migration** — CBR_TYPE discriminators unchanged, no storage format changes

## 8. Issue and Slot Cleanup

- **#375** (rename Case-prefixed SPIs): close as superseded. File new issue referencing this spec for the CbrRecord implementation.
- **Slot 199** (neocortex + engine): archive. Engine side is void (D3). Implementation is single-repo (neocortex) with coordinated downstream updates.

## References

- [casehubio/neocortex#376](https://github.com/casehubio/neocortex#376) — this issue
- [casehubio/neocortex#304 decisions.md](docs/specs/issue-304-neocortex-garden-platform/decisions.md) — D10: the original Resolution rename and its rationale
- [Drools v2 RuleUnit/DataStore](https://www.drools.org/) — naming philosophy: name things for what they ARE
- [CBR foundational issues (Kolodner 1993)](https://dl.acm.org/doi/10.5555/196108.196115) — CBR "case" concept
- [CMMN specification (OMG 2014)](https://www.omg.org/spec/CMMN/) — case management "case" concept
- Adversarial review round 1 — found CaseRecordStore/CaseMemoryStore collision (led to CbrRecord direction)
- Adversarial review round 2 — validated proposal v2 against construction sites, bridges, config properties
- Codebase audit — 159 files, 8 repos, 16/16 patterns fit
