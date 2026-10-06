# OCC Emotion to CAPS Reinforcement Mapping — Design Spec

**Issue:** casehubio/neocortex#417
**Epic:** casehubio/neocortex#406 (Emergent Behavioral Synthesis)
**Branch:** issue-434-appraisal-wiring

## Summary

Map OCC emotions produced by GoalAffectPhase into CAPS reinforcement
signals that modulate connection weight updates in BehavioralSynthesisPhase.
The implementation enriches the existing affect data pathway rather than
creating parallel storage — emotion type and intensity ride the same
AffectTrajectoryDecorator → graduation → CAPS flow that PAD values
already use.

## Data Flow

```
Cycle N:
  GoalAffectPhase(@37)
    → produces CognitiveEmotion (type, intensity, PAD)
    → updateNode() with PAD + emotion-type/emotion-intensity properties
    → AffectTrajectoryDecorator intercepts
    → stores domain="affect" memory with PAD + emotion attributes

Cycle N+1:
  ExperienceConsolidationPhase(@15)
    → graduates affect memory → COGNITIVE subgraph node with emotion props
  BehavioralSynthesisPhase(@19)
    → reads emotion-type from graduated node properties
    → EmotionReinforcementMapper: EmotionType → λ sign + pathway tags
    → topology scan: tags → source nodes → filtered inputActivations
    → updateWeights(intensity as β, λ sign as valence, arousal modifier)
```

The one-cycle delay is structural: GoalAffectPhase (@Priority 37) runs
after BehavioralSynthesisPhase (@Priority 19), so emotions from cycle N
are naturally consumed by CAPS in cycle N+1.

## Components

### 1. EmotionReinforcementMapper (NEW)

**Module:** mindmap-intelligence
**Package:** `io.casehub.neocortex.mindmap.intelligence.consolidation`
**Type:** pure static utility, no CDI

Maps EmotionType → `ReinforcementSignal` record:

```java
record ReinforcementSignal(double lambdaSign, Set<String> pathwayTags) {}
```

Mapping table (from research spec §2.4):

| EmotionType | λ sign | Pathway tags |
|-------------|--------|-------------|
| FEAR, FEARS_CONFIRMED | -1 | bis, threat |
| HOPE, RELIEF | +1 | bas |
| JOY, SATISFACTION, GRATIFICATION | +1 | bas |
| DISTRESS, DISAPPOINTMENT | -1 | bis |
| PRIDE, ADMIRATION | +1 | bas |
| SHAME, REPROACH | -1 | compliance |
| ANGER, RESENTMENT | -1 | fight_assert |
| LOVE, HAPPY_FOR, GRATITUDE | +1 | bas, fawn_accommodate |
| HATE, GLOATING | -1 | threat |
| PITY | +1 | fawn_accommodate |
| REMORSE | -1 | compliance |

Implementation: `EnumMap<EmotionType, ReinforcementSignal>` populated in
a static initializer. Single static method:

```java
static ReinforcementSignal forEmotion(EmotionType type)
```

Returns the signal for any of the 22 EmotionType values. No configuration —
the mapping is grounded in OCC theory.

### 2. GoalAffectPhase Modification

**Module:** mindmap-intelligence
**File:** `GoalAffectPhase.java`

After producing the dominant `CognitiveEmotion` and constructing the
`NodeUpdate` with PAD values, also set two properties:

- `emotion-type` → `EmotionType.name()` (e.g., `"FEAR"`)
- `emotion-intensity` → `String.valueOf(intensity)` (e.g., `"0.73"`)

These are added to the same `updateNode()` call via
`NodeUpdate.withPropertiesToSet()`. No additional store calls.

The OCC path already produces `CognitiveEmotion` — this change adds
two property writes to the existing node update. Legacy path (no
GoalAppraisal) is unchanged.

### 3. AffectTrajectoryDecorator Modification

**Module:** mindmap (CDI wiring)
**File:** `AffectTrajectoryDecorator.java`

Currently stores only PAD values via `AffectEvents.toMemoryInput()`.
The modification:

1. After the PAD-change guard passes, check `update.propertiesToSet()`
   for keys `emotion-type` and `emotion-intensity`
2. If present, include them as additional attributes on the MemoryInput

This requires either:
- An overload of `AffectEvents.toMemoryInput()` that accepts extra
  attributes (`Map<String, String> extraAttributes`), or
- Building the MemoryInput directly in the decorator with the merged
  attribute map

The decorator already has access to the `NodeUpdate` — no new data
sources needed. Emotion properties are optional; PAD-only updates
(from non-goal nodes) continue to work as before.

### 4. BehavioralSynthesisPhase Modification

**Module:** mindmap-intelligence
**File:** `BehavioralSynthesisPhase.java`

Currently uses:
- `averageValence(nodes)` (PAD pleasure) as `outcomeValence`
- `averageIntensity(nodes)` (graduation-score, default 0.5) as `outcomeIntensity`
- `salienceMultiplier = 1.0`
- `schedule = "continuous"`

With emotion metadata, the phase branches:

**When graduated nodes have `emotion-type` property:**

1. Parse `EmotionType.valueOf(node.property("emotion-type"))`
2. Look up `EmotionReinforcementMapper.forEmotion(type)` → λ sign + tags
3. Build tag→source-node index from `CapsEngine.topology()`:
   scan all connections, group source nodes by their connection tags
4. Construct `inputActivations` map: for each source node in the
   target tags' group, set activation = 1.0
5. Compute `salienceMultiplier = 1.0 + β × |arousal|` where arousal
   comes from the node's PAD and β is a configurable constant
   (default 0.3, per spec §5.3)
6. Parse `emotion-intensity` → `outcomeIntensity` (β_outcome)
7. Use `lambdaSign` as `outcomeValence`
8. Call `CapsEngine.updateWeights()` with these values

**When nodes lack `emotion-type` (backward compatible):**

Fall back to existing behavior — PAD pleasure as valence,
graduation-score as intensity, salienceMultiplier = 1.0.

**Tag→source-node index:** Built once per consolidation run by scanning
`CapsTopology.connections()`. Cached for the duration of the run.
Structure: `Map<String, Set<String>>` (tag → set of source node IDs).

### 5. Dependency Addition

Add explicit `<dependency>` on `casehub-neocortex-cognitive-api` to
`mindmap-intelligence/pom.xml`. Currently available transitively via
cognitive-index, but direct usage of `EmotionType` requires an explicit
dependency per Maven best practice.

## Testing

### EmotionReinforcementMapper

Unit tests verifying:
- All 22 EmotionType values have a mapping (no gaps)
- λ sign correctness for representative types (FEAR → -1, JOY → +1)
- Pathway tags correctness for representative types
- `forEmotion()` returns non-null for every enum value

### GoalAffectPhase

Existing tests extended to verify:
- OCC path sets `emotion-type` property on goal node
- OCC path sets `emotion-intensity` property on goal node
- Legacy path does NOT set emotion properties
- Properties round-trip through the node update

### AffectTrajectoryDecorator

Existing tests extended to verify:
- PAD update WITH emotion properties stores all attributes
- PAD update WITHOUT emotion properties stores PAD only (backward compat)
- Emotion properties without PAD change do not trigger storage

### BehavioralSynthesisPhase

Integration tests verifying:
- Graduated nodes with emotion-type trigger tag-filtered weight updates
- λ sign determines reinforcement direction (positive vs negative valence)
- Emotion intensity maps to β_outcome (clamped by CapsWeightUpdater)
- PAD arousal amplifies salienceMultiplier
- Graduated nodes without emotion-type use existing PAD-based fallback
- Tag→source-node index correctly resolves pathway connections

## Non-Goals

- **ActionAppraisalObserver integration** — the spec notes action-based
  OCC emotions are available immediately (no one-cycle delay). This is
  a future enhancement; #417 covers goal-based emotions only.
- **CapsEngine SPI changes** — the existing `updateWeights()` API is
  sufficient. No tag-filter parameter needed.
- **Configurable mapping table** — the OCC→CAPS mapping is theoretical,
  not domain-specific. Hardcoded EnumMap is appropriate.

## References

- Research spec §2.4 (OCC↔CAPS integration mechanism, mapping table,
  timing semantics) — casehubio/neocortex#407 psychology cause-effect models
- Research spec §5.1 (Rescorla-Wagner equation: ΔW = α × β × (λ − predicted))
- Research spec §5.3 (arousal learning rate modifier: 1 + β × |arousal|)
- casehubio/neocortex#408 (CAPS graph engine — parent issue)
- `caps-engine/CapsWeightUpdater.java` — Rescorla-Wagner implementation
- `caps-engine/` topology YAML — connection tags (bas, bis, threat, etc.)
- `mindmap-intelligence/GoalAffectPhase.java` — OCC emotion producer
- `mindmap/AffectTrajectoryDecorator.java` — affect memory storage
- `mindmap-intelligence/BehavioralSynthesisPhase.java` — CAPS consumer
- `cognitive-api/EmotionType.java` — OCC emotion enum
- `cognitive-api/CognitiveEmotion.java` — emotion record with type/intensity/PAD
- D1 (decisions.md) — emotion type persistence via enriched affect pathway
- D2 (decisions.md) — tag-filtered weight updates for pathway targeting
- D3 (decisions.md) — mapping utility in mindmap-intelligence
