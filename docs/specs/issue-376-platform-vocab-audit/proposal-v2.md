# Platform Vocabulary Proposal v2 — Unified Case Vocabulary

## Guiding Principles

1. **"Case" is the platform concept.** casehub is about cases. All vocabulary radiates from "Case."
2. **Name things for what they ARE.** Concrete, self-descriptive names — not abstract AI terminology. `CaseRecordStore` not `CaseBase`. `CaseRecord` not `Exemplar`.
3. **Engine is frozen.** `CaseInstance`, `PlanItem`, `SubCaseGroup` — settled. Not touched.
4. **The CBR subsystem renames.** CBR types move from `Cbr`-prefixed names to `Case`-compound names.

## The Case Lifecycle Story

A **CaseInstance** (engine) is executed → plan items run → the execution produces a **CaseRecord** (CBR) → that record is stored in the **CaseRecordStore** → future cases retrieve similar records to inform decisions.

## Data Types

| Current | Proposed | What it IS |
|---------|----------|-----------|
| `CbrCase` | `CaseRecord` | A recorded past case — problem, solution, outcome, features |
| `ResolvedCase` (was `PlanCbrCase`) | `CasePlanRecord` | A case record capturing plan execution steps |
| `ResolutionGuide` (was `TextualCbrCase`) | `CaseTextRecord` | A case record capturing textual guidance |
| `FeatureVectorCbrCase` | `CaseFeatureRecord` | A case record capturing feature vectors for similarity |
| `ResolutionStep` (was `PlanTrace`) | `CasePlanStep` | A step within a plan case record |
| `GuidanceStep` | `CaseTextStep` | A step within a textual case record |
| `ScoredCbrCase<T>` | `ScoredCaseRecord<T>` | A case record scored by similarity retrieval |
| `CbrCase.cbrType()` | `CaseRecord.recordType()` | Discriminator accessor |
| `CbrOutcome` | `CaseOutcome` | Execution outcome feedback |

CBR_TYPE discriminator VALUES stay unchanged ("plan", "textual", "feature-vector") — no data migration.

## Store / Infrastructure Types

Composite SPI stays as ONE SPI (not split into T-Box/A-Box top-level SPIs).
ISP sub-interfaces provide the separation for consumers who need it.

| Current | Proposed | Role |
|---------|----------|------|
| `CbrCaseMemoryStore` (composite) | `CaseRecordStore` (composite) | Stores and retrieves case records |
| `CbrCaseStore` (ISP) | `CaseRecordOps` | Store operations |
| `CbrCaseRetriever` (ISP) | `CaseRecordRetrieval` | Similarity retrieval |
| `CbrCaseLifecycle` (ISP) | `CaseRecordLifecycle` | Supersede, reinstate, erase |
| `CbrCaseAdmin` (ISP) | `CaseRecordAdmin` | Schema registration, reconciliation |
| `DelegatingCbrCaseMemoryStore` | `DelegatingCaseRecordStore` | Forwarding base class |

## Decorator / Implementation Types

| Current | Proposed |
|---------|----------|
| `OutcomeWeightingCbrCaseMemoryStore` | `OutcomeWeightingCaseRecordStore` |
| `DiversityCbrCaseMemoryStore` | `DiversityCaseRecordStore` |
| `TrustWeightedCbrCaseMemoryStore` | `TrustWeightedCaseRecordStore` |
| `ScopeDecayCbrCaseMemoryStore` | `ScopeDecayCaseRecordStore` |
| `TrendEnrichmentCbrCaseMemoryStore` | `TrendEnrichmentCaseRecordStore` |
| `TemporalDecayCbrCaseMemoryStore` | `TemporalDecayCaseRecordStore` |
| `SupersessionNotificationCbrCaseMemoryStore` | `SupersessionNotificationCaseRecordStore` |
| `ErasureNotificationCbrCaseMemoryStore` | `ErasureNotificationCaseRecordStore` |
| `TrackingCbrCaseMemoryStore` | `TrackingCaseRecordStore` |
| `CrossEncoderCbrCaseMemoryStore` (if exists) | `CrossEncoderCaseRecordStore` |
| `InMemoryCbrCaseMemoryStore` | `InMemoryCaseRecordStore` |
| `QdrantCbrCaseMemoryStore` | `QdrantCaseRecordStore` |
| `JpaCbrCaseMemoryStore` | `JpaCaseRecordStore` |
| `NoOpCbrCaseMemoryStore` | `NoOpCaseRecordStore` |

## CDI Event Types

| Current | Proposed |
|---------|----------|
| `CbrCasesErased` | `CaseRecordsErased` |
| `CbrCasesSuperseded` | `CaseRecordsSuperseded` |
| `CbrCasesReinstated` | `CaseRecordsReinstated` |
| `CbrRetrievalRecorded` | `CaseRetrievalRecorded` |
| `CbrRetrievalTrace` | `CaseRetrievalTrace` |
| `CbrRetrievalTracker` | `CaseRetrievalTracker` |
| `CbrAdaptationRecorded` | `CaseAdaptationRecorded` |
| `CbrEnsembleRecorded` | `CaseEnsembleRecorded` |

## Adaptation Types (engine bridge)

| Current | Proposed | Rationale |
|---------|----------|-----------|
| `PlanAdapter` | `CasePlanAdapter` | Bridges CaseRecord → engine Plan |
| `AdaptedPlan` | `AdaptedPlan` | **Unchanged** — engine-side output |
| `AdaptedStep` | `AdaptedStep` | **Unchanged** — engine vocabulary (bindingName, workerName) is correct |
| `AdaptationAction` | `AdaptationAction` | **Unchanged** |
| `AdaptationTrace` | `CaseAdaptationTrace` | Audit trail for adaptation |
| `PlanEnsembleAnalyzer` | `CasePlanEnsembleAnalyzer` | Cross-plan structural analysis |
| `EnsemblePlan` | `EnsemblePlan` | **Unchanged** — engine-side output |

## Query / Config Types

| Current | Proposed |
|---------|----------|
| `CbrQuery` | `CaseRecordQuery` |
| `CbrFeatureSchema` | `CaseRecordSchema` |
| `CbrFeatureValidator` | `CaseRecordValidator` |
| `CbrSimilarityScorer` | `CaseSimilarityScorer` |
| `CbrFilter` | `CaseRecordFilter` |
| `CbrRetentionPolicy` | `CaseRetentionPolicy` |

## Frozen (no change)

- **Engine:** `CaseInstance`, `CaseMetaModel`, `SubCaseGroup`, `PlanItem`, `PlanItemRecord`, `TaskStatus`
- **Memory:** `CaseMemoryStore`, `GraphCaseMemoryStore`, `CaseEnrichmentStep`
- **RAG:** `CaseRetriever`, `CaseContextRetriever`
- **Cognitive:** `Confidence`, `ConfidenceOrigin`, `TemporalMark`
- **MindMap:** `MindMapStore`, `MindMapNode`
- **Engine vocabulary in CBR types:** `bindingName`, `capabilityName`, `workerName` in `CasePlanStep` / `AdaptedStep` — this is correct coupling, not a leak

## Migration Strategy

### Phase 1: Introduce new interfaces with @Deprecated bridges

```java
// New primary interface
public interface CaseRecord { 
    String recordType();  // was cbrType()
    String problem();
    String solution();
    // ... 
}

// Bridge — old name extends new, downstream code still compiles
@Deprecated
public interface CbrCase extends CaseRecord {
    @Deprecated
    default String cbrType() { return recordType(); }
}

// Records implement CbrCase (which IS-A CaseRecord)
public record CasePlanRecord(...) implements CbrCase { ... }
```

Same pattern for the store:
```java
public interface CaseRecordStore { ... }

@Deprecated  
public interface CbrCaseMemoryStore extends CaseRecordStore { ... }
```

### Phase 2: Downstream migration

- Apps replace `CbrCase` → `CaseRecord` in their code (at their own pace)
- Construction: `new CasePlanRecord(...)` replaces `new ResolvedCase(...)` or `new PlanCbrCase(...)`
- Factory methods on `CaseRecord` ease construction migration

### Phase 3: Remove deprecated bridges

After all consumers have migrated, remove the `@Deprecated` interfaces.

## Package Strategy

Package stays `io.casehub.neocortex.memory.cbr` during migration.
Optional future rename to `io.casehub.neocortex.memory.caserecord` after bridges are removed — one rename, not two.

## Open Questions

1. **T-Box/A-Box SPI split:** Proposal keeps ONE composite (`CaseRecordStore`). ISP sub-interfaces separate concerns. Is this sufficient, or should schema registration be a separate top-level SPI?
2. **`ScoredCaseRecord<T>` readability:** Is `ScoredCaseRecord<CasePlanRecord>` acceptable, or does the "Case" repetition need addressing? Alternative: `CaseMatch<CasePlanRecord>`.
3. **`CaseRecordStore` vs `CaseMemoryStore` confusion:** Are "Record" and "Memory" sufficiently distinct? "Record" = structured, schema-defined, similarity-searchable. "Memory" = episodic, domain-partitioned, text-searchable.
