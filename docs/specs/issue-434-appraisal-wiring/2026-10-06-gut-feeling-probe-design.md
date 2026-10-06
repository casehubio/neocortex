# Runtime Gut Feeling Probe Design — #409

## Overview

A lightweight affect-similarity probe for novel situations. When the LLM
agent encounters a situation, the probe queries past experience memories
for semantic resonance and falls back to PAD-space mood-distance comparison.
The result is a transient, pre-cognitive signal — approach, avoid, or
cautious — rendered alongside crystallized behavioral attractors.

## Scope

- Two-layer probe: experience-memory semantic search + PAD mood-distance
  fallback
- GutFeelingParticipant (CognitionTickParticipant, DERIVED phase)
- GutFeeling record type + GutValence enum in cognition-api
- Extend BehavioralPromptSection with transient gut signal rendering
- Update DirectiveSection to reflect combined behavioral+gut framing
- Gut feelings are **ephemeral** — cached in the participant for render-time
  consumption, never persisted to CaseMemoryStore

## Architecture

### Data Flow

```
CognitionTickContext.observation (current situation text)
  → GutFeelingParticipant.tick() (DERIVED phase)
    → Layer 1: ExperienceQuery.search() via CaseMemoryStore
      → top-3 by RELEVANCE, filter null-PAD, mean PAD → valence
    → Layer 2 (fallback): MemoryScanRequest (domain="affect")
      → scan last 50 affect memories, compare mood PAD via Euclidean distance
    → GutFeeling (cached per agent/tenant)
      → BehavioralPromptSection.render() (reads cached result)
        → transient signal zone in prompt output
```

### Trigger

The probe **always runs** during the DERIVED tick phase (it's cheap — one
query call). BehavioralPromptSection always renders gut signals alongside
crystallized attractors. The directive framing tells the LLM that
crystallized patterns are stronger than transient signals — no coupling
needed between the probe and attractor matching logic.

## Component Design

### 1. GutFeeling Record (cognition-api)

```java
package io.casehub.neocortex.cognition.gut;

public record GutFeeling(
    GutValence valence,
    double intensity,
    @Nullable String resonanceDescription
) {}
```

### 2. GutValence Enum (cognition-api)

```java
public enum GutValence {
    APPROACH, AVOID, CAUTIOUS
}
```

Derived from aggregated pleasure:
- pleasure > 0.1 → APPROACH
- pleasure < -0.1 → AVOID
- within ±0.1 → CAUTIOUS

### 3. GutFeelingParticipant (cognition module)

Concrete class implementing `CognitionTickParticipant` (same pattern as
`AppraisalTickParticipant` — the @FunctionalInterface provides `tick()`,
and the class adds `currentResult()` for render-time access).

DERIVED phase (same as AppraisalTickParticipant).

**Constructor dependencies (all nullable for graceful degradation):**
- `@Nullable CaseMemoryStore` — for experience-memory query and affect-memory scan. When null, probe returns empty.
- `@Nullable MoodOrchestrator` — for current mood PAD (fallback layer). When null, Layer 2 is skipped.

**State:** `ConcurrentHashMap<String, GutFeeling>` keyed by
`agentId + ":" + tenantId`. Cleared as the **first operation** in
`tick()` (no `beginTick()` lifecycle hook exists on the SPI).

**`tick(CognitionTickContext context)`:**

1. **Layer 1 — Experience-memory semantic probe:**
   - If `context.observation()` is null, skip to Layer 2.
   - Query: `ExperienceQuery.search(agentId, tenantId, observation)`
     with `.withLimit(3).withOrder(MemoryOrder.RELEVANCE)`.
   - Filter results to memories where `pleasure() != null`.
   - If ≥1 result with PAD: compute mean pleasure, arousal, dominance.
   - Classify: pleasure > 0.1 → APPROACH, < -0.1 → AVOID, else CAUTIOUS.
   - Intensity: `Math.min(1.0, Math.abs(meanPleasure))`.
   - ResonanceDescription: join the text of matched memories, truncated
     to 100 chars. E.g. "past authority criticism, hierarchical conflict."
   - Cache and return.

2. **Layer 2 — PAD mood-distance fallback:**
   - If Layer 1 produced a result, skip.
   - Get current mood: `moodOrchestrator.currentMood(agentId, tenantId)`.
   - If no mood, return empty — no gut feeling.
   - Scan: `MemoryScanRequest(tenantId, "affect", null, null, 50, null)`.
   - Filter to memories with non-null PAD.
   - Compute centroid of scanned affect memories (mean pleasure, arousal,
     dominance).
   - Euclidean distance from current mood to centroid.
   - If distance < 0.3: no resonance — current mood is near the baseline.
     Return empty.
   - If distance ≥ 0.3: resonance detected. Valence from current mood's
     pleasure dimension (same thresholds). Intensity from distance
     clamped to [0, 1].
   - ResonanceDescription from PAD quadrant: map mood to qualitative
     label. E.g. "high-arousal negative states" (pleasure < 0, arousal > 0),
     "low-arousal positive states" (pleasure > 0, arousal < 0).
   - Cache and return.

**`currentResult(String agentId, String tenantId)` → `Optional<GutFeeling>`:**
Reads from the cache. Returns empty if no gut feeling was computed.

### 4. BehavioralPromptSection Extension

Add a second constructor parameter: `@Nullable GutFeelingParticipant`.

In `render()`, after rendering crystallized attractors, append transient
gut signal if present:

```
You have a deeply ingrained completionist drive that's been strengthening —
rooted in task abandonment and deadline pressure. This situation also
resonates with past authority criticism — you feel a cautious wariness
(transient, may not apply here).
```

**Gut signal rendering:**
- "This situation also resonates with [resonanceDescription]"
- Valence as natural language: APPROACH → "you feel drawn to engage",
  AVOID → "you feel like pulling back", CAUTIOUS → "you feel a cautious
  wariness"
- Always tagged "(transient, may not apply here)" — explicit framing
  that this is weaker than crystallized patterns

If no gut feeling, render crystallized attractors only (no change to
existing behavior).

### 5. CognitionCore Integration

In `promptSections()`, when creating BehavioralPromptSection:

```java
if (config.behavioralEnabled() && mindMapStore != null) {
    sections.add(new BehavioralPromptSection(mindMapStore, gutFeelingParticipant));
}
```

The `gutFeelingParticipant` field is added to CognitionCore, populated
during tick participant setup. When null (gut feeling not configured),
BehavioralPromptSection renders crystallized attractors only.

### 6. CognitionConfig

No new config flag. The gut feeling probe is gated by:
- `behavioralEnabled` (existing — controls the section)
- CaseMemoryStore availability (graceful degradation)
- MoodOrchestrator availability (graceful degradation for Layer 2)

Adding a separate `gutFeelingEnabled` would be over-engineering — the
feature is an enhancement to BehavioralPromptSection, not an independent
subsystem.

### 7. DirectiveSection Update

Update the `BehavioralPromptSection` directive to reflect combined content:

```
"These are your behavioral patterns. Established patterns are deep
tendencies shaped by accumulated experience — let them color your
responses naturally. Transient signals are gut reactions to the
current situation — weaker, contextual, and may not apply. When they
conflict, established patterns take precedence:"
```

## Testing

### GutFeelingParticipant tests

- No observation + no mood → returns empty
- Observation matches experience memories with PAD → correct valence
  and intensity
- Observation matches but memories have null PAD → skips to fallback
- No experience match + mood present + distance ≥ 0.3 → fallback fires
- No experience match + mood near centroid (< 0.3) → returns empty
- No experience match + no mood → returns empty
- ResonanceDescription from experience memory text
- ResonanceDescription from PAD quadrant (fallback)
- Per-agent isolation

### BehavioralPromptSection extension tests

- Null GutFeelingParticipant → renders crystallized only (backward compat)
- Gut feeling present → renders crystallized + transient zone
- Gut feeling with no attractors → renders transient only
- Transient signal includes "(transient, may not apply here)"
- Each GutValence renders correct natural language

## References

- BehavioralPromptSection.java — just implemented in #410 (extension target)
- AppraisalTickParticipant.java — tick participant pattern
- CaseMemoryStore.java — query(MemoryQuery) + scan(MemoryScanRequest) SPIs
- ExperienceQuery.java — search(agentId, tenantId, question) factory
- MemoryOrder.RELEVANCE — semantic ranking
- MoodOrchestrator.java — currentMood(agentId, tenantId)
- AffectEvents.java — affect memory structure (domain="affect", PAD fields)
- Memory.java — pleasure()/arousal()/dominance() accessors
- CognitionTickContext.java — observation field
- CognitionPhase.DERIVED — tick phase
- decisions-409.md — D1-D8 design decisions
- GitHub #409 — focal issue
- GitHub #406 — parent epic
