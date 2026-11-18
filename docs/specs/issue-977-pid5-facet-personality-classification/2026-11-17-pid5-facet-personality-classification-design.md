# PID-5 Facet Extension for Personality-Aware Situation Classification

**Issue:** casehubio/neocortex#977
**Epic:** casehubio/neocortex#841
**Date:** 2026-11-17

## Problem

The situation classifier uses disposition axes (socialOrient, ruleFollowing, riskAppetite, autonomy, conflictMode) to differentiate personality-aware classification. This captures *direction* (cooperative vs competitive) but not *magnitude* (how intensely a character expresses a trait).

Two characters with identical disposition axes but different PID-5 profiles perceive situations differently. An anxious cooperative character reads social ambiguity as threatening; a calm cooperative character reads it as neutral. The classifier currently treats them identically.

## Solution

Extend the personality metadata pipeline to include PID-5 facet scores and derived AppraisalWeights alongside disposition axes. Three touchpoints change: metadata enrichment, prompt construction, and CBR cache keying.

## Architecture

### Metadata Enrichment

Rename `addDispositionMetadata()` → `addPersonalityMetadata()` in both `BehavioralSynthesisPhase` and `DriveProfileDerivationPhase`. The method already accesses `CognitiveDefaultsRegistry.forAgent(agentId)` and extracts `DispositionAxes`. Extend it to also extract:

1. **PID-5 facets** from `defaults.descriptor().personalityFacets()` — each facet stored as `pid5.<FacetName>=<score>` (2dp, e.g. `pid5.Anxiousness=0.80`). Only facets present in the configured inclusion list are added.

2. **AppraisalWeights** from `defaults.appraisalWeights()` — each field stored as `appraisal.<fieldName>=<value>` (2dp, e.g. `appraisal.urgencyWeight=1.45`).

Existing `disposition.*` keys unchanged. The metadata map grows by up to 18 entries (13 facets + 5 appraisal fields).

### Prompt Construction

Extend `PersonalityPromptBuilder` to read `pid5.*` and `appraisal.*` metadata keys. Two new static methods:

- `pid5Suffix(Map<String, String> metadata)` — formats PID-5 facets as a natural-language line. Facets with score >= 0.7 labelled "high", <= 0.3 labelled "low", otherwise "moderate". Example output:
  ```
  Personality facets: high Anxiousness (0.80), low Risk Taking (0.25), moderate Withdrawal (0.55).
  ```

- `appraisalSuffix(Map<String, String> metadata)` — formats AppraisalWeights as interpretive prose describing perception biases. Example:
  ```
  Perception biases: elevated urgency sensitivity (1.45), heightened fear onset (1.60), strict self-standards (1.60).
  ```
  Values near 1.0 (within ±0.15) are omitted as neutral. Values > 1.0 described as "elevated"/"heightened"/"strict"; < 1.0 as "reduced"/"dampened"/"lenient".

**Path A** (low tension): Append both suffixes to the user prompt after the disposition line, before the "Classify the situation..." instruction.

**Path C** (high tension): Weave PID-5 and appraisal descriptions into the personality system prompt after the disposition description and tension narrative.

### CBR Cache Key

Extend `CbrCachingSituationDecorator.computeCaseId()`. After the existing disposition axis entries in the SHA-256 digest, append:

1. PID-5 facet scores in **alphabetical facet name order**, each as `<FacetName>=<2dp score>` separated by null bytes. Only configured facets are included.

2. AppraisalWeights fields in **fixed field order** (fearOnsetThreshold, otherStandardsStrictness, relationshipWeight, selfStandardsStrictness, urgencyWeight — alphabetical), each as `<fieldName>=<2dp value>`.

PID-5 facets are **not** added as `CbrFilter` predicates on the CBR query. Facets are continuous — the cache key hash provides exact-match discrimination, and embedding similarity handles the semantic matching. Disposition axes remain as categorical filters.

Extend `addDispositionFeatures()` → `addPersonalityFeatures()` to store PID-5 scores and appraisal weights as `FeatureValue.string()` features in the CBR record. Keys: `pid5-<FacetName>` and `appraisal-<fieldName>` (dash-delimited, matching the disposition convention).

Extend `DISPOSITION_KEYS` → `PERSONALITY_KEYS` or add parallel arrays for PID-5 and appraisal keys.

### Configuration

Add to `ClassificationCbrConfig` (or a sibling `ClassificationPid5Config`):

```java
@WithDefault("Anxiousness,Emotional Lability,Withdrawal,Impulsivity,Risk Taking,Suspiciousness,Intimacy Avoidance,Callousness,Manipulativeness,Grandiosity,Deceitfulness,Attention Seeking,Hostility")
String pid5Facets();
```

Both `PersonalityPromptBuilder` and `CbrCachingSituationDecorator` read this to determine which facets to include. Parsing: split on comma, trim, validate against `PersonalityFacet` constants.

### Model Version Bump

Bump the default `modelVersion` in `ClassificationCbrConfig` from `"haiku"` to `"haiku-pid5"` (or similar). This invalidates all existing cache entries, ensuring old disposition-only cached results aren't returned for personality-enriched queries. The model-version filter on CBR queries provides this isolation naturally.

## Testing

### Unit Tests

**PersonalityPromptBuilder:**
- `pid5Suffix` formats high/low/moderate labels correctly
- `pid5Suffix` respects configured facet inclusion list
- `appraisalSuffix` omits near-neutral values
- `appraisalSuffix` uses correct directional labels

**CbrCachingSituationDecorator:**
- Same PID-5 scores produce same cache key
- Different PID-5 scores produce different cache key
- Same disposition + different PID-5 produce different cache key (the core scenario)
- Backward compatible: no PID-5 metadata produces same hash as before (guard against accidental cache invalidation outside the model-version bump)

### Eval Test

Extend `PersonalityClassificationEvalTest` with two new profiles:

| Profile | Disposition | PID-5 delta |
|---------|------------|-------------|
| anxious-cooperative | cooperative/strict/conservative/low/avoidant | ANXIOUSNESS=0.85, SUSPICIOUSNESS=0.75 |
| calm-cooperative | cooperative/strict/conservative/low/avoidant | ANXIOUSNESS=0.20, SUSPICIOUSNESS=0.25 |

Same disposition axes → same tension routing (both Path A, tension 0.0). Different PID-5 scores.

**Assertions:**
- Activation divergence (cosine distance) between anxious-cooperative and calm-cooperative exceeds baseline (> 0.05)
- anxious-cooperative activates threat-related nodes (social_threat, punishment) more strongly than calm-cooperative for ambiguous social scenarios
- calm-cooperative shows lower overall activation magnitude for the same scenarios (fewer nodes above 0.3 threshold)

## Files Changed

| File | Change |
|------|--------|
| `BehavioralSynthesisPhase.java` | Rename + extend `addDispositionMetadata` → `addPersonalityMetadata` |
| `DriveProfileDerivationPhase.java` | Same rename + extension |
| `PersonalityPromptBuilder.java` | Add `pid5Suffix()`, `appraisalSuffix()`, integrate into both paths |
| `CbrCachingSituationDecorator.java` | Extend `computeCaseId()` and feature storage with PID-5 + appraisal |
| `ClassificationCbrConfig.java` | Add `pid5Facets` config property, bump model version default |
| `LlmSituationClassifierTest.java` | Tests for PID-5 prompt inclusion |
| `CbrCachingSituationDecoratorTest.java` | Tests for PID-5 cache key behaviour |
| `PersonalityClassificationEvalTest.java` | Add anxious-cooperative vs calm-cooperative profiles |

## What This Does NOT Change

- **SituationClassifier SPI** — no signature change. Personality metadata flows through the existing `Map<String, String>`.
- **DispositionTension** — tension routing stays disposition-based. PID-5 enriches the classification prompt but doesn't affect which path (A vs C) is selected. Future work could add PID-5-based tension patterns.
- **Transient state** — mood and active drives are explicitly excluded from the cache key per the issue specification. They're transient and belong in runtime prompt context, not cached personality profiles.
- **RuleBasedSituationClassifier** — keyword-based fallback is not personality-aware; PID-5 extension applies only to the LLM classifier.

## References

- `BehavioralSynthesisPhase.java:394` — existing `addDispositionMetadata` pattern
- `PersonalityPromptBuilder.java` — disposition prompt construction
- `CbrCachingSituationDecorator.java:239-256` — existing `computeCaseId` SHA-256 pattern
- `DescriptorView.java` — `personalityFacets()` accessor
- `PersonalityFacet.java` — 13 PID-5 facet constants, `isHigh()`/`isLow()` helpers
- `AppraisalWeights.java` — 5-field record with NEUTRAL sentinel
- `CognitiveDerivationEngine.java:660-728` — AppraisalWeights derivation from PID-5
- `ClassificationCbrConfig.java` — existing config mapping
- casehubio/neocortex#941 — personality-aware classification (disposition axes)
- casehubio/neocortex#976 — parent: DriveProfileDerivationPhase personality classification
