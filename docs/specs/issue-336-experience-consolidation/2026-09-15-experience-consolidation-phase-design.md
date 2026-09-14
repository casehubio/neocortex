# ExperienceConsolidationPhase — Design Spec

**Issue:** casehubio/neocortex#336
**Date:** 2026-09-15
**Branch:** issue-336-experience-consolidation
**Depends on:** #322 (cognitive node types — landed), #323 (consolidateNow — landed), #295 (consolidation pipeline — landed)

## 1. Problem Statement

The three-tier memory model has Tier 2 (episodic buffer — experience events stored as memories with domain="experience" in CaseMemoryStore) and Tier 3 (knowledge graph — typed nodes in MindMapStore). Consolidation runs five phases (AccessFrequency, MergeDetection, SchemaDiscovery, CommunitySummary, CuriosityRefresh) but none bridge Tier 2 events into Tier 3.

Characters accumulate experience events during gameplay (observations, actions, outcomes) via ExperienceStream. These are persisted as memories but never graduate to the knowledge graph. Without graduation, the mindmap has no representation of what the agent learned from experience — only what was extracted from conversation text via ConversationBridge.

## 2. Design

### 2.1 Overview

A new `ConsolidationPhase` at @Priority(15) that:

1. Scans recent experience memories from CaseMemoryStore (cursor-based pagination)
2. Scores each for graduation worthiness via `GraduationScorer` SPI
3. Classifies above-threshold events into cognitive types via `GraduationClassifier` SPI
4. Creates typed mindmap nodes in the COGNITIVE subgraph
5. Advances the cursor

The phase is a thin pipeline orchestrator. Scoring and classification are pluggable SPIs with sensible defaults that work without LLM access or downstream dependencies.

### 2.2 Phase Ordering

| Priority | Phase | Relationship |
|----------|-------|-------------|
| 10 | AccessFrequencyPhase | Flush counters — runs first |
| **15** | **ExperienceConsolidationPhase** | **Creates new cognitive nodes** |
| 20 | MergeDetectionPhase | Detects duplicates among new + existing nodes |
| 25 | SchemaDiscoveryPhase | Discovers property patterns in new nodes |
| 30 | CommunitySummaryPhase | Clusters including new nodes |
| 40 | CuriosityRefreshPhase | Signals updated by new nodes |

Running at @Priority(15) ensures newly graduated nodes are available for merge detection, schema discovery, and community summaries in the same consolidation tick.

### 2.3 Pipeline Flow

```
CaseMemoryStore.scan(tenantId, domain="experience", limit=MAX_PER_PASS, afterMemoryId=cursor)
    │
    ▼
For each Memory:
    ├── GraduationScorer.score(memory) → double [0, 1]
    ├── Filter: score ≥ threshold? (configurable, default 0.5)
    ├── GraduationClassifier.classify(memory) → GraduationResult
    ├── Create MindMapNode in COGNITIVE subgraph:
    │     name:        memory description (truncated to 100 chars)
    │     subgraph:    COGNITIVE
    │     cognitiveKind: result.cognitiveKind()
    │     confidence:  ConfidenceOrigin based on result
    │     provenance:  "experience-consolidation"
    │     properties:  source-memory-id, event metadata, classifier output
    └── Advance cursor to memory ID
```

## 3. SPIs

### 3.1 GraduationScorer (D1)

```java
package io.casehub.neocortex.memory.experience;

@FunctionalInterface
public interface GraduationScorer {
    double score(Memory memory);
}
```

Produces a [0, 1] score indicating graduation worthiness. Higher = more worthy.

**DefaultGraduationScorer** (@DefaultBean):

```java
@DefaultBean
@ApplicationScoped
public class DefaultGraduationScorer implements GraduationScorer {

    @Override
    public double score(Memory memory) {
        if (memory.confidence() != null) {
            return memory.confidence().value();
        }
        return 0.5;
    }
}
```

Returns the memory's confidence value, defaulting to 0.5 (neutral). This baseline ensures all events with non-null confidence are scored by their stored confidence — a reasonable proxy when no richer scoring is available.

Downstream consumers (blocks, wacky-manor) can provide @Alternative @Priority implementations that compute composite scores from surprise, arousal, and other signals derived from memory attributes.

### 3.2 GraduationClassifier (D2)

```java
package io.casehub.neocortex.memory.experience;

public interface GraduationClassifier {
    GraduationResult classify(Memory memory);
}
```

**GraduationResult:**

```java
public record GraduationResult(
    String cognitiveKind,
    ConfidenceOrigin confidenceOrigin,
    Map<String, String> properties
) {
    public GraduationResult {
        Objects.requireNonNull(cognitiveKind, "cognitiveKind required");
        Objects.requireNonNull(confidenceOrigin, "confidenceOrigin required");
        if (properties == null) properties = Map.of();
    }
}
```

- `cognitiveKind` — one of the cognitive types from #322: belief, intention, prediction, judgment, fear, desire. Determines trait rule activation (Belieflike, Intentionlike, etc.).
- `confidenceOrigin` — STATED for explicit observations, INFERRED for derived classifications, SPECULATED for uncertain.
- `properties` — additional properties to set on the node (e.g., subject, status, basis from the trait interface contracts).

**DefaultGraduationClassifier** (@DefaultBean):

```java
@DefaultBean
@ApplicationScoped
public class DefaultGraduationClassifier implements GraduationClassifier {

    @Override
    public GraduationResult classify(Memory memory) {
        String eventType = memory.attributes().getOrDefault(
            ExperienceAttributeKeys.EVENT_TYPE, "observation");

        return switch (eventType) {
            case "observation" -> classifyObservation(memory);
            case "action"      -> classifyAction(memory);
            case "outcome"     -> classifyOutcome(memory);
            default            -> new GraduationResult("belief",
                                      ConfidenceOrigin.INFERRED, Map.of());
        };
    }

    private GraduationResult classifyObservation(Memory memory) {
        String subject = memory.attributes().get(
            ExperienceAttributeKeys.SUBJECT);
        Map<String, String> props = new HashMap<>();
        if (subject != null) props.put("subject", subject);
        props.put("status", "active");
        return new GraduationResult("belief",
            ConfidenceOrigin.STATED, props);
    }

    private GraduationResult classifyAction(Memory memory) {
        String capability = memory.attributes().get(
            ExperienceAttributeKeys.CAPABILITY);
        Map<String, String> props = new HashMap<>();
        if (capability != null) props.put("goal", capability);
        props.put("status", "active");
        return new GraduationResult("intention",
            ConfidenceOrigin.INFERRED, props);
    }

    private GraduationResult classifyOutcome(Memory memory) {
        String result = memory.attributes().get(
            ExperienceAttributeKeys.RESULT);
        Map<String, String> props = new HashMap<>();
        if (result != null) props.put("target", result);
        return new GraduationResult("judgment",
            ConfidenceOrigin.INFERRED, props);
    }
}
```

The default mapping:
- Observation (subject-bearing) → Belieflike with STATED confidence
- Action (capability-bearing) → Intentionlike with INFERRED confidence
- Outcome (result-bearing) → Evaluative with INFERRED confidence

This is deliberately simple. Disposition-aware classification (the same observation producing a Fear vs a Belief depending on agent personality) belongs in downstream consumers that implement GraduationClassifier with access to CognitiveDefaults and agent context.

### 3.3 Why two SPIs, not one (D8)

Scoring and classification are independent concerns:

- **Scoring** answers: "Is this event worth remembering?" Pure salience assessment.
- **Classification** answers: "What kind of knowledge does this produce?" Interpretive.

Blocks can provide a rich scorer (surprise/arousal/confidence composite) without touching classification, and wacky-manor can provide a disposition-aware classifier without reimplementing scoring. A single `ExperienceGraduator` SPI would couple these, forcing implementors to handle both even when they only care about one.

## 4. Cursor Tracking (D3)

`MemoryQuery` requires non-empty subjects — it cannot query all experience memories for a tenant without knowing agent IDs. The phase uses `CaseMemoryStore.scan(MemoryScanRequest)` instead, which supports domain-based scanning with cursor-based pagination.

### 4.1 Scan Approach

```java
memoryStore.scan(new MemoryScanRequest(
    tenantId,
    ExperienceEvents.DOMAIN.name(),   // domain = "experience"
    null, null,                        // no attribute filter
    maxPerPass,
    lastProcessedMemoryId              // cursor — null on first run
))
```

`MemoryScanRequest.afterMemoryId` provides cursor-based pagination. The cursor is the ID of the last processed memory. On first run (null cursor), the scan starts from the beginning.

### 4.2 Cursor Storage

The cursor is stored as a property on a sentinel node in the TYPE_SYSTEM subgraph:

```
Node name:  "_consolidation-state"
Subgraph:   TYPE_SYSTEM
Property:   graduation-cursor = <memory ID>
```

On first run for a tenant (no sentinel node exists), the phase creates the sentinel with null cursor — processing all historical experience memories. Subsequent runs resume from the stored cursor.

This approach:
- Survives JVM restarts (persistent via MindMapStore backend)
- Is per-tenant (each tenant has its own TYPE_SYSTEM subgraph)
- Follows the TypeRegistry pattern of using TYPE_SYSTEM for infrastructure state
- Requires no new SPIs, tables, or modules

### 4.3 Cursor Advancement

The cursor advances to the ID of the last processed memory after all nodes for that pass are created. If the phase throws an exception mid-pass, the cursor is not advanced — the next tick reprocesses the batch. This provides at-least-once semantics.

Duplicate nodes from reprocessing are handled by MergeDetectionPhase (@Priority 20). Additionally, the phase checks for an existing node with the same `source-memory-id` property before creating, as a fast-path dedup guard.

## 5. Node Creation (D4, D6)

### 5.1 One Node Per Event

Each graduated experience event produces one mindmap node (D4). Related events that share a subject are not batched — MergeDetectionPhase handles deduplication in the same tick.

### 5.2 Node Properties

| Property | Source |
|----------|--------|
| name | Memory content, truncated to 100 chars |
| subgraph | COGNITIVE (via findOrCreateSubgraph) |
| cognitiveKind | From GraduationResult |
| confidence | From GraduationResult.confidenceOrigin + scorer output |
| provenance | `"experience-consolidation"` |
| source-memory-id | Memory ID — audit trail (D6) |
| graduation-score | Scorer output — diagnostic |
| event-type | From ExperienceAttributeKeys.EVENT_TYPE |
| agent-id | From memory subject (agent ID) |
| pleasure | From Memory.pleasure() — PAD affective dimension (nullable) |
| arousal | From Memory.arousal() — PAD affective dimension (nullable) |
| dominance | From Memory.dominance() — PAD affective dimension (nullable) |
| Additional | From GraduationResult.properties (subject, status, basis, etc.) |

### 5.3 Subgraph Routing

All graduated nodes go to the COGNITIVE subgraph (#322). The phase uses the same `findOrCreateSubgraph` pattern as `MindMapExtractor.findOrCreateSubgraph()` — query `listSubgraphs()`, find by type, create if absent. See `findOrCreateCognitiveSubgraph()` in §7.

### 5.4 Trait Activation

After node creation, the existing trait rule infrastructure fires automatically:
- `cognitiveKind=belief` → Belieflike trait via declarative rule
- `cognitiveKind=intention` → Intentionlike trait
- `cognitiveKind=judgment` → Evaluative trait
- etc.

Compositional rules also fire: a node with `cognitiveKind=belief` and a `timeframe` property gains both Belieflike and Predictive traits.

No additional wiring needed — `TraitApplicationDecorator` (@Priority 70) intercepts `addNode` and evaluates all `TraitRule` instances (including declarative rules from `cognitive-traits.yaml`) against the new node. Matching rules add traits automatically.

## 6. Rate Limiting (D7)

Configurable max-per-pass cap, default 20: `casehub.consolidation.graduation.max-per-pass`.

The scan to CaseMemoryStore uses `limit=maxPerPass` to bound the result set. Events beyond the cap are processed in subsequent ticks. The cursor only advances to the last event actually processed, so unprocessed events remain in the scan window.

## 7. ExperienceConsolidationPhase Implementation

```java
package io.casehub.neocortex.mindmap.intelligence.consolidation;

@ApplicationScoped
@Priority(15)
public class ExperienceConsolidationPhase implements ConsolidationPhase {

    private final CaseMemoryStore memoryStore;
    private final MindMapStore mindMapStore;
    private final GraduationScorer scorer;
    private final GraduationClassifier classifier;
    private final double threshold;
    private final int maxPerPass;

    @Inject
    public ExperienceConsolidationPhase(
            CaseMemoryStore memoryStore,
            MindMapStore mindMapStore,
            Instance<GraduationScorer> scorer,
            Instance<GraduationClassifier> classifier,
            Instance<ExperienceConsolidationConfig> config) {
        this.memoryStore = memoryStore;
        this.mindMapStore = mindMapStore;
        this.scorer = scorer.isResolvable() ? scorer.get() : new DefaultGraduationScorer();
        this.classifier = classifier.isResolvable() ? classifier.get() : new DefaultGraduationClassifier();
        ExperienceConsolidationConfig c = config.isResolvable() ? config.get() : null;
        this.threshold = c != null ? c.threshold() : 0.5;
        this.maxPerPass = c != null ? c.maxPerPass() : 20;
    }

    @Override
    public String name() { return "experience-consolidation"; }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        String cursor = loadCursor(tenantId);

        List<Memory> experiences = memoryStore.scan(
            new MemoryScanRequest(tenantId,
                ExperienceEvents.DOMAIN.name(),
                null, null,
                maxPerPass,
                cursor));

        if (experiences.isEmpty()) return;

        String subgraphId = findOrCreateCognitiveSubgraph(tenantId);
        Set<String> existingSourceIds = loadExistingSourceMemoryIds(tenantId);
        String lastProcessedId = cursor;

        for (Memory memory : experiences) {
            try {
                double score = scorer.score(memory);
                if (score < threshold) {
                    lastProcessedId = memory.memoryId();
                    continue;
                }

                if (existingSourceIds.contains(memory.memoryId())) {
                    lastProcessedId = memory.memoryId();
                    continue;
                }

                GraduationResult result = classifier.classify(memory);

                Map<String, String> properties = new HashMap<>(result.properties());
                properties.put("source-memory-id", memory.memoryId());
                properties.put("graduation-score", String.valueOf(score));
                properties.put("event-type",
                    memory.attributes().getOrDefault(
                        ExperienceAttributeKeys.EVENT_TYPE, "unknown"));
                properties.put("agent-id", memory.subject().id());
                properties.put("cognitiveKind", result.cognitiveKind());

                String name = memory.text().length() > 100
                    ? memory.text().substring(0, 100) + "..."
                    : memory.text();

                NodeInput nodeInput = NodeInput.of(name, subgraphId)
                        .withConfidence(MindMapConfidenceDefaults.forOrigin(
                            result.confidenceOrigin(), Instant.now()))
                        .withProvenance("experience-consolidation")
                        .withProperties(properties);

                if (memory.pleasure() != null) nodeInput = nodeInput.withPleasure(memory.pleasure());
                if (memory.arousal() != null) nodeInput = nodeInput.withArousal(memory.arousal());
                if (memory.dominance() != null) nodeInput = nodeInput.withDominance(memory.dominance());

                mindMapStore.addNode(nodeInput, tenantId);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to graduate memory "
                    + memory.memoryId() + " for tenant " + tenantId, e);
            }
            lastProcessedId = memory.memoryId();
        }

        if (lastProcessedId != null && !lastProcessedId.equals(cursor)) {
            saveCursor(tenantId, lastProcessedId);
        }
    }

    private String findOrCreateCognitiveSubgraph(String tenantId) {
        return mindMapStore.listSubgraphs(tenantId).stream()
            .filter(sg -> SubgraphTypes.COGNITIVE.equals(sg.type()))
            .map(MindMapSubgraph::id)
            .findFirst()
            .orElseGet(() -> mindMapStore.createSubgraph(
                new SubgraphInput("Cognitive", SubgraphTypes.COGNITIVE, null),
                tenantId));
    }

    private Set<String> loadExistingSourceMemoryIds(String tenantId) {
        return mindMapStore.search(
                MindMapQuery.of(tenantId, 1000).withType(SubgraphTypes.COGNITIVE))
            .stream()
            .map(n -> n.property("source-memory-id"))
            .flatMap(Optional::stream)
            .collect(Collectors.toSet());
    }
}
```

### 7.1 Scan Approach

`MemoryQuery` requires non-empty subjects, making it unsuitable for cross-agent domain scans. The phase uses `CaseMemoryStore.scan(MemoryScanRequest)` which supports domain-based scanning with cursor-based pagination — no subject list required. The scan returns memories ordered by store insertion, with `afterMemoryId` providing the cursor for resumption (§4).

### 7.2 Dedup Guard

`loadExistingSourceMemoryIds(tenantId)` builds a `Set<String>` of all `source-memory-id` property values from existing cognitive nodes in a single upfront query per pass. The per-memory check is then O(1) set membership. This avoids O(N×M) per-memory scanning and handles the crash recovery reprocessing case correctly.

The upfront query uses `MindMapStore.search(MindMapQuery.of(tenantId, 1000).withType(SubgraphTypes.COGNITIVE))` and extracts source-memory-id properties in Java. For agent-scale graphs (hundreds of cognitive nodes), this is acceptable. For larger graphs, a property-based lookup API on MindMapStore would be a follow-on optimization.

## 8. Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `casehub.consolidation.graduation.threshold` | `0.5` | Minimum score for graduation |
| `casehub.consolidation.graduation.max-per-pass` | `20` | Maximum nodes created per pass |

Configuration follows the `SchemaDiscoveryConfig` pattern — an optional `@ConfigMapping` interface with `Instance<>` graceful degradation.

```java
@ConfigMapping(prefix = "casehub.consolidation.graduation")
public interface ExperienceConsolidationConfig {
    @WithDefault("0.5") double threshold();
    @WithDefault("20") int maxPerPass();
}
```

## 9. Module Impact

SPIs live in **memory-api** (where `Memory`, `ReflectionSynthesizer`, and `PlanAdapter` already live). Downstream consumers (blocks) depend on memory-api, not mindmap-intelligence — placing SPIs in mindmap-intelligence would force blocks to take a heavy implementation dependency.

Implementations and the phase live in **mindmap-intelligence** alongside existing consolidation phases.

**memory-api** (SPI declarations):

| File | Change |
|------|--------|
| `GraduationScorer.java` | NEW — @FunctionalInterface SPI in `io.casehub.neocortex.memory.experience` |
| `GraduationClassifier.java` | NEW — classification SPI in `io.casehub.neocortex.memory.experience` |
| `GraduationResult.java` | NEW — classification result record in `io.casehub.neocortex.memory.experience` |

**mindmap-intelligence** (implementations + phase):

| File | Change |
|------|--------|
| `DefaultGraduationScorer.java` | NEW — @DefaultBean, confidence passthrough |
| `DefaultGraduationClassifier.java` | NEW — @DefaultBean, metadata-driven mapping |
| `ExperienceConsolidationPhase.java` | NEW — @Priority(15) ConsolidationPhase |
| `ExperienceConsolidationConfig.java` | NEW — @ConfigMapping |

**Prerequisite:** `InMemoryMemoryStore` must be extended with `SCAN` capability support. Currently only `SqliteMemoryStore` and `JpaMemoryStore` implement `scan(MemoryScanRequest)`. Adding `SCAN` to `InMemoryMemoryStore` is infrastructure the platform needs and enables testing without SQLite.

No database migrations. No new modules. No dependency changes — mindmap-intelligence already depends on memory-api.

## 10. Testing Strategy

All tests use InMemoryMindMapStore and InMemoryMemoryStore. No SQLite, Docker, or ONNX models.

### ExperienceConsolidationPhase (12 tests)

1. Graduated event produces cognitive node in COGNITIVE subgraph with correct cognitiveKind
2. Score below threshold — event not graduated
3. Max-per-pass cap — only N events processed, watermark advanced to Nth
4. Cursor persistence — second run skips already-processed events
5. Cursor on restart — loads from sentinel node, resumes correctly
6. Dedup guard — reprocessed memory (same source-memory-id) does not create duplicate node
7. Empty experience set — no nodes created, watermark not advanced
8. Node properties — source-memory-id, graduation-score, event-type, agent-id all set
9. Provenance — node provenance is "experience-consolidation"
10. Error isolation — scorer exception for one memory doesn't block others
11. Trait activation — Belieflike trait fires for cognitiveKind=belief node
12. Multi-tenant — each tenant has independent watermark and processing

### GraduationScorer (3 tests)

13. Default scorer returns confidence value
14. Default scorer returns 0.5 when confidence is null
15. Custom scorer scores above/below threshold correctly

### GraduationClassifier (6 tests)

16. Observation → Belieflike with STATED confidence and subject property
17. Action → Intentionlike with INFERRED confidence and goal property
18. Outcome → Evaluative with INFERRED confidence and target property
19. Unknown event type → Belieflike fallback
20. Custom classifier overrides default mapping
21. Properties from classifier appear on created node

## 11. Deferred Items

| Item | Reason | Track as |
|------|--------|----------|
| Sub-threshold event handling | Issue says "pruned or retained at lower salience." Current design skips sub-threshold events, leaving them in the memory store indefinitely. Retention/pruning is a MemoryHygieneOrchestrator concern. | Follow-up issue |
| MemoryHygieneOrchestrator integration | Issue says "should integrate with retention policies." MemoryHygieneOrchestrator is in blocks — neocortex can't depend on it. Integration point is the graduated memory's attributes (downstream can mark memories as graduated). | Follow-up issue |
| PAD population on experience memories | `ExperienceEvents.toMemoryInput()` passes null for pleasure/arousal/dominance. The PAD transfer code in the phase is future-proofing — it works when PAD values become available but is currently dead code. | Follow-up on ExperienceEvents |
| Default classifier coverage | Default produces only 3 of 6 cognitive types (belief, intention, judgment). Prediction, fear, desire require a custom GraduationClassifier. | By design — downstream consumer responsibility |
| SchemaDiscoveryPhase interaction | SchemaDiscoveryPhase iterates only `subgraphPriority` from curiosity signals. A new COGNITIVE subgraph with no prior curiosity signals may not be discovered in the same tick. | Acceptable — subsequent ticks pick it up |

## References

- ConsolidationScheduler.java:107-144 — tick() method, phase orchestration
- ConsolidationPhase.java:5-8 — SPI contract
- SchemaDiscoveryPhase.java — latest phase pattern, @Priority(25), Instance<> graceful degradation
- AccessFrequencyPhase.java — per-tick state pattern (beginTick/cachedSnapshot)
- ExperienceEvent.java:6-17 — sealed hierarchy (Observation, Action, Outcome)
- ExperienceEvents.java:19-66 — toMemoryInput conversion, domain="experience"
- ExperienceAttributeKeys.java — EVENT_TYPE, SUBJECT, CAPABILITY, RESULT keys
- ExperienceQuery.java — query helper factories
- ConversationBridge.java:40-77 — text → nodes pipeline (Tier 1→3 analogue)
- MindMapExtractor.java:76-77 — COGNITIVE_TYPES set, resolveSubgraphType
- SubgraphTypes.java — COGNITIVE constant from #322
- Belieflike.java, Evaluative.java, Intentionlike.java — trait interfaces from #322
- cognitive-traits.yaml — declarative trait rules from #322
- GE-20260912-be7c74 — three-tier memory model, graduation via importance scoring
- GE-20260912-ff141b — neocortex cognitive stack overview
- Knowledge consolidation pipeline spec (issue-295) — ConsolidationPhase SPI, scheduler design
- Cognitive node types spec (issue-322) — cognitive types, trait interfaces, subgraph routing
- Social cognition spec (issue-287) — DomainActivation, perspectival queries
