# Memory Seeding Infrastructure — Design Spec

**Issue:** casehubio/neocortex#398
**Epic:** casehubio/neocortex#406 (emergent behavioral synthesis)
**Depends on:** #401 (experience-to-behaviour catalogue), #407 (CAPS topology)
**Blocks:** #397 (behavioral attractor synthesis), #402 (CognitiveEmergenceTest)
**Date:** 2026-10-03

## 1. Overview

Memory seeding infrastructure enables pre-populating an agent's cognitive
state with fabricated past events — authored backstory that the cognitive
system processes as if it were lived experience. Behaviour emerges from
backstory filtered through personality disposition rather than from
explicit prescription.

The infrastructure provides:

1. **FormativeExperience** — a new sealed permit on `ExperienceEvent` for
   backstory events carrying CAPS metadata, salience amplification, and
   catalogue provenance
2. **BackstorySeeder** — a service that reads the #401 experience-behaviour
   catalogue, translates catalogue entries into `FormativeExperience`
   events, and batch-injects them via `ExperienceRecorder`
3. **Formative-aware graduation** — extensions to `GraduationScorer` and
   `GraduationClassifier` that handle formative events: bypass
   corroboration gating and propagate formative metadata to graduated nodes
4. **Need satisfaction seeding** — direct initialization of `NeedTier`
   satisfaction levels via MindMap node properties

The pipeline follows the batch-then-consolidate pattern: all backstory
events are injected first, then a single consolidation cycle graduates
them into the knowledge graph. No direct state overrides — the CAPS
network derives behavioral attractors from the seeded experiences (D2).

**Drive intensities** (curiosity, competence, affiliation, autonomy) are
derived from experience via the existing `DriveOrchestrator` during the
first `CognitionCore` tick, consistent with D9's approach for mood. No
direct drive seeding — the drive system aggregates from experience
memories the same way mood does.

## 2. Type Evolution — FormativeExperience

### 2.1 Sealed Permit

`FormativeExperience` is added as the fourth permit on the
`ExperienceEvent` sealed interface in `memory-api`:

```java
public sealed interface ExperienceEvent
    permits Observation, Action, Outcome, FormativeExperience {
    // existing methods
}
```

### 2.2 Record Fields

```java
public record FormativeExperience(
    String agentId,
    String tenantId,
    String caseId,
    String turnId,
    Instant timestamp,
    String description,
    Double confidence,
    Map<String, String> metadata,
    String catalogueEntryId,
    List<String> situationTypes,
    double salienceMultiplier,
    String reinforcementSchedule,
    String developmentalPeriod,
    Double pleasure,
    Double arousal,
    Double dominance
) implements ExperienceEvent {
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `catalogueEntryId` | String | yes | Source catalogue entry ID (e.g., `attachment-anxious`) |
| `situationTypes` | List\<String\> | yes | Pre-classified CAPS input node IDs from the catalogue's `triggers` field |
| `salienceMultiplier` | double | yes | Learning rate multiplier. Default from developmental period: infancy=3.0, childhood=2.0, adolescence=1.5, adult=1.0 |
| `reinforcementSchedule` | String | no | Reinforcement schedule from catalogue trigger: `continuous`, `fixed_ratio`, `variable_ratio`, `fixed_interval`, `variable_interval` |
| `developmentalPeriod` | String | no | Period when experience occurred: `infancy`, `childhood`, `adolescence`, `adult` |
| `pleasure` | Double | no | Pleasure PAD dimension [-1, 1], derived from trigger node category + intensity |
| `arousal` | Double | no | Arousal PAD dimension [-1, 1], derived from trigger intensity |
| `dominance` | Double | no | Dominance PAD dimension [-1, 1], derived from trigger node category |

Common fields (`agentId`, `tenantId`, `caseId`, `turnId`, `timestamp`,
`description`, `confidence`, `metadata`) follow the same contracts as
existing permits. For backstory events, `caseId` and `turnId` are null
(no runtime case context). `confidence` defaults to 0.8 for
catalogue-sourced events (high but not certain — calibration may adjust).

### 2.3 Consumer Impact

All consumers of `ExperienceEvent` or `ExperienceRecorded` CDI events
that need updating:

**Switch expressions on ExperienceEvent:**

| Consumer | Location | Handling |
|----------|----------|----------|
| `ExperienceEvents.toMemoryInput()` | memory-api | Map FormativeExperience fields to metadata attributes + PAD fields |
| `ExperienceEvents.eventTypeName()` | memory-api | Return `"formative"` |
| `ExperienceRecorderCore` | memory-core | Record as normal (no special handling — event type discriminated in metadata) |
| `RelationshipProcessor.sourceEventType()` | memory-core | Add `case FormativeExperience f -> "formative"` to exhaustive switch |

**CDI observers of `ExperienceRecorded`:**

| Observer | Location | Handling |
|----------|----------|----------|
| `MemoryBeans.onExperienceRecorded()` → `RelationshipProcessor` | memory | Safe — formative events lack `TARGET_AGENT` metadata, filtered by existing null check |
| `SignificanceAccumulator` | mindmap-intelligence | **Requires suspension protocol** — see §5.6 |
| `CognitiveAttentionAccumulator` | mindmap-intelligence | Safe — checks `goal-node-id` metadata, absent on formative events |

### 2.4 Metadata and PAD Mapping

`ExperienceEvents.toMemoryInput()` maps FormativeExperience-specific
fields to reserved attribute keys and passes PAD values through to
MemoryInput:

```java
case FormativeExperience f -> {
    attrs.put(FormativeAttributeKeys.CATALOGUE_ENTRY_ID, f.catalogueEntryId());
    attrs.put(FormativeAttributeKeys.SITUATION_TYPES,
              String.join(",", f.situationTypes()));
    attrs.put(FormativeAttributeKeys.SALIENCE_MULTIPLIER,
              String.valueOf(f.salienceMultiplier()));
    if (f.reinforcementSchedule() != null)
        attrs.put(FormativeAttributeKeys.REINFORCEMENT_SCHEDULE,
                  f.reinforcementSchedule());
    if (f.developmentalPeriod() != null)
        attrs.put(FormativeAttributeKeys.DEVELOPMENTAL_PERIOD,
                  f.developmentalPeriod());
}
```

PAD values flow from `FormativeExperience.pleasure()/arousal()/dominance()`
into the `MemoryInput` constructor's PAD parameters (replacing the current
`null, null, null` for these fields). This ensures graduated MindMap nodes
carry emotional valence, enabling MoodOrchestrator aggregation (D9).

New `FormativeAttributeKeys` constants class in `memory-api`:

```java
public final class FormativeAttributeKeys {
    public static final String CATALOGUE_ENTRY_ID = "catalogue-entry-id";
    public static final String SITUATION_TYPES = "situation-types";
    public static final String SALIENCE_MULTIPLIER = "salience-multiplier";
    public static final String REINFORCEMENT_SCHEDULE = "reinforcement-schedule";
    public static final String DEVELOPMENTAL_PERIOD = "developmental-period";
}
```

## 3. Module Structure

### 3.1 memory-seeding Module

New Maven module `memory-seeding` with artifact ID
`casehub-neocortex-memory-seeding`. Package:
`io.casehub.neocortex.memory.seeding`.

**Dependencies:**
- `memory-api` — FormativeExperience, ExperienceRecorder
- `cognition-api` — NeedTier (in `io.casehub.neocortex.cognition.need`)
- `mindmap-api` — MindMapStore for need satisfaction seeding
- `jackson-dataformat-yaml` — catalogue YAML parsing
- `jackson-databind` — JSON/YAML ObjectMapper

**Does not depend on:** cognition (runtime), mindmap-intelligence, rag,
inference.

### 3.2 Module Contents

| Class | Purpose |
|-------|---------|
| `CatalogueLoader` | Reads catalogue YAML files via Jackson, returns `CatalogueIndex` + `List<CatalogueEntry>` |
| `BackstoryProfile` | Record: agent/tenant context + `List<CatalogueSelection>` + `Map<NeedTier, Double>` need levels |
| `CatalogueSelection` | Record: `entryId` + optional `intensityOverride` + `repetitionOverride` |
| `BackstorySeeder` | Service: accepts `BackstoryProfile`, reads catalogue, generates `FormativeExperience` events, injects via `ExperienceRecorder`, seeds need levels via `MindMapStore` |
| `FormativeTimestampGenerator` | Generates epoch-relative timestamps ordered by developmental period |
| `SalienceDefaults` | Maps developmental period → default salience multiplier |
| `PadDeriver` | Maps CAPS input node category + trigger intensity → PAD values |
| `CatalogueEntry` | Jackson-mapped record matching #401 YAML schema |
| `CatalogueIndex` | Jackson-mapped record for the index.yaml metadata |
| `FormativeGraduationScorer` | `GraduationScorer` that bypasses corroboration for formative events |
| `FormativeGraduationClassifier` | `GraduationClassifier` that propagates formative metadata to graduated nodes |

## 4. Catalogue Loading

### 4.1 CatalogueLoader

Reads the experience-behaviour catalogue YAML files from a configurable
classpath or filesystem location. Returns strongly-typed records:

```java
public class CatalogueLoader {
    public CatalogueIndex loadIndex(Path catalogueDir);
    public List<CatalogueEntry> loadAll(Path catalogueDir);
    public Optional<CatalogueEntry> findEntry(
        List<CatalogueEntry> entries, String entryId);
}
```

YAML structure maps directly to Jackson records matching the #401 spec §3
schema: `CatalogueEntry` → `id`, `model`, `clinical_name`, `description`,
`triggers` (list of `TriggerSpec`), `narratives`, `expected_outcomes`
(list of `ExpectedOutcome`), `developmental_period`, `modulating_axes`,
`related`, `sources`.

### 4.2 Validation

`CatalogueLoader` performs structural validation at load time:
- Entry IDs unique across all files
- Intensity ranges in [0, 1]
- Required fields present

**CAPS topology node validation** (trigger node IDs are valid CAPS input
nodes, expected outcome node IDs are valid CAPS output/mediating nodes)
is deferred to the build-time validation script (`scripts/validate_catalogue.py`
from #401) rather than runtime `CatalogueLoader`. Rationale: the CAPS
topology YAML is a docs-level artifact, not a compiled resource; loading
it at runtime adds a configuration dependency for a check that's already
enforced at build time.

Structural validation failures throw `IllegalStateException` at load
time — fail fast, don't seed with invalid data.

## 5. BackstorySeeder Pipeline

### 5.1 BackstoryProfile

```java
public record BackstoryProfile(
    String agentId,
    String tenantId,
    List<CatalogueSelection> selections,
    Map<NeedTier, Double> needSatisfaction
) {
    public record CatalogueSelection(
        String entryId,
        Double intensityOverride,
        Integer repetitionOverride
    ) {}
}
```

### 5.2 Seeding Flow

```
BackstoryProfile
  │
  ├─ For each CatalogueSelection:
  │    ├─ Load CatalogueEntry by entryId
  │    ├─ For each trigger in entry:
  │    │    ├─ Determine repetition count (override or catalogue default)
  │    │    ├─ For each repetition:
  │    │    │    ├─ Generate description from narrative template (if available)
  │    │    │    │  or synthetic description from clinical_name
  │    │    │    ├─ Generate timestamp (period-ordered, epoch-relative)
  │    │    │    ├─ Derive PAD values via PadDeriver (node category + intensity)
  │    │    │    ├─ Compute salience multiplier from developmental_period
  │    │    │    └─ Create FormativeExperience record
  │    │    └─ Collect events
  │    └─ Collect all events for this entry
  │
  ├─ Sort all events by timestamp (period ordering)
  ├─ Suspend SignificanceAccumulator (§5.6)
  ├─ Batch inject via ExperienceRecorder.recordAll()
  ├─ Resume SignificanceAccumulator
  │
  └─ For each NeedTier entry:
       └─ Seed satisfaction level via MindMapStore node property
```

After injection completes, the next scheduled consolidation tick
processes all formative memories in a single pass.

### 5.3 Repetition Count Mapping

Catalogue trigger `repetition` enum maps to concrete counts:

| Repetition | Count |
|------------|-------|
| `high` | 10 |
| `moderate` | 5 |
| `low` | 2 |

Override via `CatalogueSelection.repetitionOverride` replaces the
catalogue value.

### 5.4 Description Generation

For each repetition of a trigger:
1. If the entry has `narratives`: cycle through the narrative templates,
   substituting them round-robin across repetitions
2. If no narratives: generate a synthetic description from
   `clinical_name` + trigger `node` name, e.g.,
   "Experienced inconsistent care [inconsistent_care]"

### 5.5 PAD Derivation

`PadDeriver` maps CAPS input node category + trigger intensity to PAD
values on each `FormativeExperience`:

- **Pleasure:** Negative for threat/rejection/scarcity triggers, positive
  for secure/reward triggers. Magnitude scaled by trigger intensity.
- **Arousal:** Elevated for high-intensity triggers, moderate for mid-range.
- **Dominance:** Low for powerlessness/loss-of-control triggers, high for
  achievement/autonomy triggers.

Mapping is determined by the CAPS topology's input node category
(relationship, threat, achievement, social, resource, autonomy,
consequence) combined with trigger intensity range. PAD values are
carried on the `FormativeExperience` record and flow through
`ExperienceEvents.toMemoryInput()` into the `MemoryInput` constructor's
PAD parameters.

### 5.6 CDI Event Cascade — SignificanceAccumulator Suspension

`ExperienceRecorderCore.recordAll()` fires `ExperienceRecorded` CDI
events synchronously for each stored event. `SignificanceAccumulator`
(mindmap-intelligence) accumulates per-tenant significance (default 1.0
per event, threshold 10.0) and triggers `consolidateNow()` when the
threshold is crossed.

Injecting 50+ formative events (typical backstory: 5 entries × 10
high-repetition triggers) would cross this threshold after the 10th
event, triggering premature consolidation while 40+ events remain
uninjected. The cursor-based scan in `ExperienceConsolidationPhase.run()`
would advance past processed memories, leaving later events for the
next tick.

**Solution:** `BackstorySeeder` suspends the `SignificanceAccumulator`
before batch injection and resumes it after. The accumulator exposes a
`suspend()`/`resume()` protocol:

```java
public class SignificanceAccumulator {
    private volatile boolean suspended;
    public void suspend() { suspended = true; }
    public void resume()  { suspended = false; }
    // onExperienceRecorded checks suspended flag before accumulating
}
```

After injection, the seeder does NOT explicitly trigger consolidation.
Instead, the accumulated significance from injection (suppressed during
suspension) is applied on `resume()`, which triggers `consolidateNow()`
if the threshold is met. This ensures all events are stored before
consolidation begins.

### 5.7 Idempotency

`BackstorySeeder` checks for existing formative memories for the agent
before seeding:

```java
var existing = memoryStore.scan(tenantId, new MemoryScanRequest(
    Subject.of("agent", profile.agentId()),
    ExperienceEvents.DOMAIN,
    Map.of(ExperienceAttributeKeys.EVENT_TYPE, "formative"),
    1));  // limit 1 — existence check only
if (!existing.isEmpty()) {
    throw new IllegalStateException(
        "Agent " + profile.agentId() + " already has formative memories");
}
```

Repeat calls are rejected. To re-seed, the caller must erase existing
formative memories first. This is a deliberate choice — silent duplicate
injection would waste storage and spike significance accumulation.

### 5.8 Lifecycle Integration

`BackstorySeeder` is called during agent initialization, **before the
first `CognitionCore` tick**. The calling sequence:

1. Create agent identity and tenant
2. Seed cognitive defaults (personality, disposition — existing flow)
3. **Call `BackstorySeeder.seed(backstoryProfile)`** — injects formative
   memories + need satisfaction levels
4. Wait for next consolidation tick — graduates formative memories into
   knowledge graph nodes
5. First `CognitionCore` tick — MoodOrchestrator and DriveOrchestrator
   aggregate from graduated experience, producing initial mood and drive
   intensities

Step 3 is the only new step. The seeder has no ordering dependency on
personality/disposition seeding — they operate on different stores.
The ordering guarantee is: seeding completes before the first tick.

## 6. Timestamp Generation

### 6.1 FormativeTimestampGenerator

Assigns epoch-relative timestamps to maintain developmental period
ordering:

```java
public class FormativeTimestampGenerator {
    public List<Instant> generate(
        List<FormativeExperience> events,
        Instant origin  // default: Instant.EPOCH
    );
}
```

Period ordering (earliest to latest):
1. `infancy` — origin + 0 to +1 year
2. `childhood` — origin + 1 to +5 years
3. `adolescence` — origin + 5 to +10 years
4. `adult` — origin + 10 to +15 years
5. `any` — distributed across all periods

Within each period, events are spaced evenly.

### 6.2 Determinism

Given the same `BackstoryProfile` and origin, the same timestamps are
always produced. No randomisation — reproducible seeding for testing.

## 7. Graduation Integration

### 7.1 FormativeGraduationScorer

A `GraduationScorer` implementation that detects formative events and
bypasses corroboration:

```java
@Alternative @Priority(1) @ApplicationScoped
public class FormativeGraduationScorer implements GraduationScorer {

    @Inject DefaultGraduationScorer delegate;

    @Override
    public double score(Memory memory, GraduationContext context) {
        String eventType = memory.attributes()
            .getOrDefault(ExperienceAttributeKeys.EVENT_TYPE, "");
        if ("formative".equals(eventType)) {
            return memory.confidence() != null
                ? memory.confidence().value() : 0.8;
        }
        return delegate.score(memory, context);
    }
}
```

`@Alternative @Priority(1)` replaces `DefaultGraduationScorer` for the
`GraduationScorer` injection point. The delegate is injected by concrete
type — `DefaultGraduationScorer` remains a bean for its own type even
when displaced as the `GraduationScorer` provider.

### 7.2 FormativeGraduationClassifier

A `GraduationClassifier` implementation that classifies formative events
with a dedicated cognitive kind and propagates formative metadata:

```java
@Alternative @Priority(1) @ApplicationScoped
public class FormativeGraduationClassifier implements GraduationClassifier {

    @Inject DefaultGraduationClassifier delegate;

    @Override
    public GraduationResult classify(Memory memory) {
        String eventType = memory.attributes()
            .getOrDefault(ExperienceAttributeKeys.EVENT_TYPE, "");
        if ("formative".equals(eventType)) {
            var props = new HashMap<String, String>();
            propagateIfPresent(memory, props,
                FormativeAttributeKeys.CATALOGUE_ENTRY_ID);
            propagateIfPresent(memory, props,
                FormativeAttributeKeys.SITUATION_TYPES);
            propagateIfPresent(memory, props,
                FormativeAttributeKeys.SALIENCE_MULTIPLIER);
            propagateIfPresent(memory, props,
                FormativeAttributeKeys.DEVELOPMENTAL_PERIOD);
            propagateIfPresent(memory, props,
                FormativeAttributeKeys.REINFORCEMENT_SCHEDULE);
            return new GraduationResult(
                "formative-experience", ConfidenceOrigin.STATED, props);
        }
        return delegate.classify(memory);
    }
}
```

The cognitive kind `"formative-experience"` distinguishes graduated
backstory nodes from runtime observations/actions/outcomes. Downstream
consumers (#408 CAPS engine, #397 behavioral attractor synthesis) can
filter for formative nodes by cognitive kind and read `situation-types`
and `salience-multiplier` from node properties.

## 8. Need Satisfaction Seeding

### 8.1 Mechanism

`BackstorySeeder` seeds `NeedTier` satisfaction levels as MindMap node
properties on the agent's need nodes:

```java
for (var entry : profile.needSatisfaction().entrySet()) {
    var needNode = findOrCreateNeedNode(
        profile.tenantId(), entry.getKey());
    store.updateNode(needNode.id(),
        NodeUpdate.empty().withPropertiesToSet(Map.of(
            "satisfaction", String.valueOf(entry.getValue()),
            "resting-level", String.valueOf(entry.getValue()))),
        profile.tenantId());
}
```

### 8.2 Future Extension Point

The API is designed so that a future `NeedSatisfactionPhase`
(consolidation) can adjust satisfaction levels from accumulated
experience patterns. The direct seeding here sets the initial state;
the consolidation phase would modulate it over time.

## 9. Testing Strategy

### 9.1 Unit Tests (memory-seeding)

| Test Class | Covers |
|------------|--------|
| `CatalogueLoaderTest` | YAML loading, structural validation, entry lookup |
| `BackstorySeederTest` | End-to-end: profile → events → injection (mock ExperienceRecorder), idempotency rejection |
| `FormativeTimestampGeneratorTest` | Period ordering, determinism, edge cases |
| `SalienceDefaultsTest` | Period → multiplier mapping |
| `PadDeriverTest` | Node category + intensity → PAD mapping |
| `FormativeGraduationScorerTest` | Bypass for formative, delegation for runtime |
| `FormativeGraduationClassifierTest` | Formative classification + metadata propagation, delegation for runtime |

### 9.2 Unit Tests (memory-api)

| Test Class | Covers |
|------------|--------|
| `FormativeExperienceTest` | Record validation, null handling, PAD fields |
| `ExperienceEventsTest` | Extended to cover FormativeExperience → MemoryInput mapping incl. PAD |

### 9.3 Integration Tests

| Test | Covers |
|------|--------|
| `BackstorySeedingIntegrationTest` | Full pipeline: profile → seeder → in-memory store → consolidation → graduated nodes with formative metadata |

Uses `InMemoryMemoryStore` + `InMemoryMindMapStore`. Verifies that
seeded memories graduate without corroboration, carry correct metadata
(including situation-types, salience-multiplier on graduated nodes),
PAD values propagate, and need satisfaction nodes are created.

## 10. Scope Boundaries

### In scope for #398
- `FormativeExperience` sealed permit on `ExperienceEvent` (with PAD fields)
- `FormativeAttributeKeys` constants
- `memory-seeding` module: CatalogueLoader, BackstoryProfile,
  BackstorySeeder, FormativeTimestampGenerator, SalienceDefaults,
  PadDeriver, FormativeGraduationScorer, FormativeGraduationClassifier
- Need satisfaction seeding via MindMapStore
- SignificanceAccumulator suspension protocol
- Switch expression updates in ExperienceEvents, ExperienceRecorderCore,
  RelationshipProcessor
- Unit and integration tests

### Out of scope
- CAPS network processing of seeded events → #408
- Behavioral attractor synthesis from graduated nodes → #397
- Experience-derived need satisfaction adjustment → future issue
- Experience-derived drive intensity adjustment → handled by existing
  DriveOrchestrator during first tick, no new code needed
- CognitiveEmergenceTest validation framework → #402
- Runtime gut feeling probe → #409

## 11. References

- `memory-api/.../experience/ExperienceEvent.java` — sealed interface being extended
- `memory-api/.../experience/ExperienceEvents.java:64` — toMemoryInput PAD parameter positions
- `memory-api/.../experience/ExperienceAttributeKeys.java` — reserved attribute keys
- `memory-api/.../experience/ExperienceRecorder.java` — ingestion interface
- `memory-api/.../MemoryInput.java` — storage record with PAD fields
- `memory-api/.../GraduationScorer.java` — scorer SPI
- `memory-api/.../GraduationClassifier.java` — classifier SPI
- `memory-api/.../GraduationContext.java` — corroboration context
- `memory-core/.../ExperienceRecorderCore.java` — recording implementation with CDI event firing
- `memory-core/.../relationship/runtime/RelationshipProcessor.java:57` — exhaustive switch needing new case
- `mindmap-intelligence/.../consolidation/ExperienceConsolidationPhase.java` — graduation pipeline
- `mindmap-intelligence/.../consolidation/SignificanceAccumulator.java` — significance-triggered consolidation
- `mindmap-intelligence/.../consolidation/CognitiveAttentionAccumulator.java` — attention signal observer
- `cognitive-index/.../CognitiveDefaultsRegistry.java` — Jackson YAML precedent
- `cognition-api/.../need/NeedTier.java` — need tier enum (cognition-api, not memory-api)
- `mindmap-api/.../NodeUpdate.java` — withPropertiesToSet(Map) API
- `docs/specs/experience-behaviour-catalogue/` — #401 catalogue YAML files
- `docs/specs/2026-10-02-caps-topology.yaml` — CAPS node vocabulary
- `scripts/validate_catalogue.py` — build-time CAPS node validation
- Wacky Manor `ManorCognitiveSeeder` (casehubio/examples) — existing need seeding pattern (reference only, not in neocortex)
- D2 (experience injection principle) — decisions.md
- D5-D14 (#398 decisions) — decisions.md
- #401 spec §8 (integration points)
- #407 spec §5.1 (Rescorla-Wagner weight update)
