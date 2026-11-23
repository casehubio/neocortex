# Direct CAPS Settling Pathway for Dissonance

**Issue:** casehubio/neocortex#1212
**Parent:** casehubio/neocortex#593
**Branch:** issue-593-cognitive-dissonance
**Status:** Design approved
**Literature:** Festinger 1957, Shultz & Lepper 1996, Cooper & Fazio 1984

---

## 1. Overview

Extends the cognitive dissonance system (#593) with a direct CAPS settling pathway. Currently, dissonance-derived behavioural effects flow indirectly: DissonancePromptSection renders tension → character behaviour → experience recording → graduation (1+ ticks) → BehavioralSynthesisPhase settling (1+ ticks). This indirect pathway is psychologically faithful but delays CAPS behavioural effects by 2+ consolidation ticks.

The direct pathway injects dissonance-derived CAPS activations — already computed by the LLM during contradiction detection — into BehavioralSynthesisPhase's settling pass. Both sides of each contradiction are injected as competing CAPS input activations, letting the settler resolve the conflict through its constraint-satisfaction algorithm. This implements Shultz & Lepper (1996) literally: beliefs as competing activations in a connectionist network, settled to minimize constraint violations.

Both pathways coexist: the direct path provides same-tick CAPS behavioural effects, while the indirect path captures the character's conversational response to tension across subsequent ticks. They operate at different timescales and reinforce each other.

### What Changes

1. **DissonanceDetectionPhase** moves from `@Priority(25)` to `@Priority(17)` — runs before BehavioralSynthesisPhase (19) so its activations are available for the settling pass.
2. **BehavioralSynthesisPhase** gains a `mergeDissonanceActivations()` method that reads DissonanceDetectionPhase's in-memory cache and merges tension-strength-scaled CAPS activations into the combined activation map.
3. **DissonancePromptSection** continues to render (both pathways coexist).

---

## 2. Pipeline Reorder

### Before

```
ExperienceConsolidationPhase      @Priority(15)
SubThoughtConsolidationPhase      @Priority(16)
DriveAdaptationPhase              @Priority(17)
RelationshipStagePhase            @Priority(18)
BehavioralSynthesisPhase          @Priority(19)
...
DissonanceDetectionPhase          @Priority(25)
```

### After

```
ExperienceConsolidationPhase      @Priority(15)
SubThoughtConsolidationPhase      @Priority(16)
DriveAdaptationPhase              @Priority(17)  (shared)
DissonanceDetectionPhase          @Priority(17)  ← moved, shares with DriveAdaptation
RelationshipStagePhase            @Priority(18)
BehavioralSynthesisPhase          @Priority(19)
```

DissonanceDetectionPhase shares @Priority(17) with DriveAdaptationPhase. The two phases have no data dependency — DissonanceDetectionPhase reads Belieflike nodes and behavioral attractors from MindMap (previous-tick state), while DriveAdaptationPhase reads drive state. Arbitrary execution order between them is safe. Sharing priorities is established practice (#593 originally shared @Priority(24) with MergeDetectionPhase).

Behavioral attractors in the BEHAVIORAL subgraph are stable across ticks — they change only when BehavioralSynthesisPhase processes new graduated experiences. For contradiction detection, stale-by-one-tick attractors produce equivalent detection results except in the rare edge case of a single-tick attractor shift from emotionally-charged experiences.

---

## 3. Activation Injection

### Data Flow

```
DissonanceDetectionPhase (@Priority 17)
  ├─ Detects contradictions (LLM call, as before)
  ├─ Each contradiction produces:
  │   ├─ capsActivationsA: { "acceptance": 0.7, "sharing": 0.5 }
  │   ├─ capsActivationsB: { "dominance": 0.8, "competition": 0.7 }
  │   └─ strength: 0.85
  ├─ Persists tension nodes in MindMap (as before)
  └─ Updates in-memory cache (as before)

BehavioralSynthesisPhase (@Priority 19)
  ├─ Step 1: findUnprocessedGraduated() → experience nodes
  ├─ Step 2: Classify via SituationClassifier → experienceActivations
  ├─ Step 3: Copy to combinedActivations
  ├─ Step 4a: mergeDriveIntensities(combinedActivations, agentId, tenantId)
  ├─ Step 4b: mergeDissonanceActivations(combinedActivations, agentId, tenantId)  ← NEW
  ├─ Step 5: capsEngine.settle(state, combinedActivations) → SettlingResult
  └─ Steps 6-10: Persist attractors, update weights, record CBR outcomes
```

### SPI: `DissonanceActivationSource` (cognition-api)

```java
@FunctionalInterface
public interface DissonanceActivationSource {
    List<TensionActivation> activeTensions(String cacheKey);
}
```

```java
public record TensionActivation(
    Map<String, Double> capsActivationsA,
    Map<String, Double> capsActivationsB,
    double strength
) {}
```

`DissonanceDetectionPhase` in the `cognition` module implements `DissonanceActivationSource`, returning its cached `TensionSnapshot` data as `TensionActivation` records.

### `mergeDissonanceActivations()` Method (BehavioralSynthesisPhase)

```java
@Inject
Instance<DissonanceActivationSource> dissonanceActivationSource;

private void mergeDissonanceActivations(Map<String, Double> combinedActivations,
                                        String agentId, String tenantId) {
    if (!dissonanceActivationSource.isResolvable()) {
        return;
    }

    String cacheKey = tenantId + ":" + agentId;
    List<TensionActivation> tensions = dissonanceActivationSource.get()
        .activeTensions(cacheKey);
    if (tensions == null || tensions.isEmpty()) {
        return;
    }

    for (TensionActivation tension : tensions) {
        double strength = tension.strength();
        mergeActivationMap(combinedActivations, tension.capsActivationsA(), strength);
        mergeActivationMap(combinedActivations, tension.capsActivationsB(), strength);
    }
}

private void mergeActivationMap(Map<String, Double> target,
                                Map<String, Double> source, double scale) {
    if (source == null) return;
    for (var entry : source.entrySet()) {
        double scaled = entry.getValue() * scale;
        target.merge(entry.getKey(), scaled, Math::max);
    }
}
```

### Scaling

Each activation value is `llmConfidence * tensionStrength`:
- A fresh, strong tension (strength 1.0) produces full activations
- A decaying tension (strength 0.3) produces 30% activations
- A near-resolved tension (strength 0.16) produces near-zero activations

This couples CAPS behavioural effects to the same lifecycle that governs PAD adjustment and prompt rendering.

### Merge Strategy

`Math::max` — the same strategy used for drives and experience activations. Per CAPS input node, the highest activation from any source wins. This means dissonance activations only matter when they exceed the experience-derived or drive-derived activation for the same input node. This is intentional: dissonance creates *additional* pressure, it doesn't override what the character is already doing.

---

## 4. Competing Activations and CAPS Dynamics

The core theoretical claim: injecting both sides of a contradiction as competing CAPS activations implements Shultz & Lepper (1996) constraint satisfaction.

### Example

A character believes "I am a loyal friend" (activations: `acceptance: 0.7, sharing: 0.5`) but also "I would betray anyone for power" (activations: `dominance: 0.8, competition: 0.7`). At tension strength 0.85:

- Scaled activations A: `acceptance: 0.60, sharing: 0.43`
- Scaled activations B: `dominance: 0.68, competition: 0.60`

These are merged into the combined activation map. The CAPS settler then processes them alongside experience-derived and drive-derived activations. Because `acceptance` and `dominance` have inhibitory connections in the CAPS topology, the settler must resolve the conflict — the network cannot have both high acceptance and high dominance simultaneously. The resulting attractor pattern reflects the resolution.

### Oscillation Risk

When competing activations are strong and roughly equal, the settler may oscillate. CapsSettler already handles this:
- Convergence detection within bounded iterations (configurable, default 100)
- Oscillation detection produces `SettlingResult.Type.OSCILLATION`
- BehavioralSynthesisPhase already handles oscillation results (creates attractor with reduced strength)

No additional oscillation handling is needed for dissonance activations.

---

## 5. Interaction with Existing Pathways

### Direct Path (this design)

Same-tick: DissonanceDetectionPhase (17) → BehavioralSynthesisPhase (19). CAPS effects appear immediately in the attractor landscape.

### Indirect Path (from #593)

Multi-tick: DissonancePromptSection → character conversation → experience recording → ExperienceConsolidationPhase graduation → BehavioralSynthesisPhase classification → settling. 2+ ticks delay.

### Reinforcement

The two paths reinforce each other:
1. **Direct**: Immediate attractor shift — the character's behavioural tendencies change in the same tick the contradiction is detected
2. **Indirect**: Gradual conversational adaptation — the character's behaviour during conversation generates experiences that further reinforce or modify the attractor landscape

Over time, the indirect path produces experience-derived activations that align with or diverge from the dissonance-derived activations. The `Math::max` merge ensures the stronger signal wins per input node — no double-counting.

### No Double Write-Cycle

Because DissonanceDetectionPhase now runs *before* BehavioralSynthesisPhase (not after), there is exactly one `capsEngine.settle()` call per tick. The D4 concern from #593 is resolved by pipeline reordering, not by architectural complexity.

---

## 6. CDI Wiring

### Module Dependency Constraint

`mindmap-intelligence` depends on `cognition-api` (not `cognition` runtime). `cognition` depends on `mindmap-intelligence`. Direct injection of `DissonanceDetectionPhase` from BehavioralSynthesisPhase would create a circular dependency.

### Solution: SPI in cognition-api

`DissonanceActivationSource` interface and `TensionActivation` record live in `cognition-api`. BehavioralSynthesisPhase injects `Instance<DissonanceActivationSource>`:

```java
@Inject
Instance<DissonanceActivationSource> dissonanceActivationSource;
```

`DissonanceDetectionPhase` in `cognition` implements `DissonanceActivationSource`, making it available as a CDI bean. Using `Instance<T>` provides graceful degradation:
- If dissonance is disabled or the phase is not on the classpath, `isResolvable()` returns false and `mergeDissonanceActivations()` is a no-op
- No startup failure, no config flag needed beyond the existing `casehub.cognition.dissonance.enabled`

---

## 7. Configuration

No new configuration properties. The existing `casehub.cognition.dissonance.enabled` gates the entire dissonance system including the CAPS activation injection (via `Instance<DissonanceDetectionPhase>.isResolvable()`).

---

## 8. Component Changes

| Component | Module | Change |
|---|---|---|
| `DissonanceActivationSource` | cognition-api | **New** — `@FunctionalInterface` SPI |
| `TensionActivation` | cognition-api | **New** — record carrying activation maps + strength |
| `DissonanceDetectionPhase` | cognition | `@Priority(25)` → `@Priority(17)`, implements `DissonanceActivationSource` |
| `BehavioralSynthesisPhase` | mindmap-intelligence | Add `mergeDissonanceActivations()`, add `Instance<DissonanceActivationSource>` injection |

No changes to: caps-api, caps-engine, mindmap-api, DissonanceTickParticipant, DissonancePromptSection, DissonanceDetectionConfig.

---

## 9. Evaluation: Does the Direct Path Produce Richer Effects?

### Temporal Fidelity

The indirect path delays CAPS effects by 2+ ticks. For typical consolidation intervals (minutes to hours), this means a character detects a contradiction, renders felt tension, but only shifts behavioural attractors after subsequent conversation has occurred. The direct path provides same-tick attractor shifts — the character's behavioural landscape changes as soon as the contradiction is detected.

This matters most for strong contradictions (strength > 0.7) where the character should exhibit immediate behavioural ambivalence, not wait for conversational evidence.

### Constraint Satisfaction Fidelity

Shultz & Lepper (1996) model dissonance as competing activations settled through a constraint network. The indirect path approximates this — the character behaves in tension-influenced ways, and those behaviours eventually create CAPS activations through graduation. But the CAPS settler never sees the original competing activations; it sees derivative experience-based classifications. The direct path gives the settler the actual competing activation maps, letting it resolve the conflict using the full constraint-satisfaction mechanism.

### Emergent Behavioural Variety

With the direct path, the CAPS settler may produce attractor patterns that the indirect path would not. When competing belief activations enter the network alongside experience-derived activations, the resulting attractor reflects the interaction of all three sources. This can produce transitional behavioural states (e.g., oscillation between approach and avoidance) that wouldn't emerge from the indirect path alone, where experience classification flattens the tension into a single situational label.

### Recommendation

The direct path produces meaningfully richer behavioural effects because it gives the CAPS settler the actual constraint-satisfaction inputs rather than derivative classifications. The same-tick temporal fidelity is a bonus, but the core value is constraint-satisfaction fidelity. Both paths should coexist.

---

## 10. Testing Strategy

| Test Type | What It Verifies |
|---|---|
| Unit: mergeDissonanceActivations | Scaling by tension strength, Math::max merge, empty/null handling, graceful degradation when dissonance disabled |
| Unit: Pipeline ordering | DissonanceDetectionPhase runs before BehavioralSynthesisPhase (priority assertion) |
| Integration: Direct path | Belieflike contradiction → detection → activations in cache → BehavioralSynthesisPhase consumes → settling produces attractor reflecting the conflict |
| Integration: Coexistence | Both direct and indirect paths produce CAPS effects over sequential ticks without interference |
| Oscillation: Competing activations | Strong equal-strength contradictions produce oscillation result (not crash) |

---

## 11. Follow-On Items

| Item | Description |
|---|---|
| Coping integration (#595) | Coping strategies that resolve dissonance should also affect the direct CAPS pathway — when a tension is resolved, its activations drop out of the merge |
| CBR outcome tracking | Whether dissonance-influenced settling results should feed CBR outcome recording (currently out of scope) |

---

## References

- Festinger, L. (1957). *A Theory of Cognitive Dissonance*. Stanford University Press.
- Shultz, T.R. & Lepper, M.R. (1996). Cognitive dissonance reduction as constraint satisfaction. *Psychological Review*, 103(2), 219-240.
- Cooper, J. & Fazio, R.H. (1984). A new look at dissonance theory. *Advances in Experimental Social Psychology*, 17, 229-266.
- `mindmap-intelligence/.../BehavioralSynthesisPhase.java` — `mergeDriveIntensities()`, `findUnprocessedGraduated()`, settling flow
- `cognition/.../DissonanceDetectionPhase.java` — detection, tension lifecycle, in-memory cache, `capsActivationsA`/`capsActivationsB`
- `caps-engine/.../CapsSettler.java` — constraint satisfaction settling, oscillation detection
- `caps-engine/.../DriveCompetitionDetector.java` — `DRIVE_CAPS_MAPPINGS` static lookup
- casehubio/neocortex#593 — cognitive dissonance design spec, D4 (effect path), D6 (architecture approach)
- D1-D5 in `decisions.md` — this design's decisions with trade-offs and alternatives
