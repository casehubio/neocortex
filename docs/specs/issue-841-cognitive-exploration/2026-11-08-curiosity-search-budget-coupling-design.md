# Curiosity → Search Budget Coupling Design

**Issue:** casehubio/neocortex#847
**Parent:** casehubio/neocortex#846 (Cognitive exploration integration)
**Date:** 2026-11-08
**Decisions:** [847-decisions.md](847-decisions.md)

## Problem

The cognitive exploration subsystem has three independently functional components — curiosity (#596), search budget (#825), and recurring thought continuum (#826). Curiosity channel data (hedonic type, comprehension, focus type, gap proximity) doesn't shape search budget parameters. SearchBudgetTickParticipant only sees curiosity through a binary "is CURIOSITY the dominant drive?" check with fixed offsets (+0.15 breadth, +0.1 laterality, −0.1 convergence). The rich four-channel curiosity model is invisible to the exploration parameter computation.

This means an agent with specific Berlyne focus and high gap proximity (close to understanding something) gets the same search budget as one with diversive focus and low comprehension (confused and scanning broadly). Both get the same +0.15 breadth bonus if curiosity happens to be the dominant drive.

## Solution

Wire CuriosityChannel output into SearchBudgetTickParticipant as an additive modulation layer that runs after the existing drive/mood/gut/threat computation. Channel modulation magnitudes are configurable per-agent via a new ChannelModulationConfig record, derived from personality by CDE.

## Architecture

### Data Flow

```
CuriosityDrive.evaluate()
  ├── computes DriveIntensity (existing)
  ├── caches lastGaps() (existing)
  └── computes+caches lastChannel() (NEW)
        └── delegates to CuriosityChannelComputer.compute()

SearchBudgetTickParticipant.participate()
  ├── base sigmoid from composite drive intensity (existing)
  ├── drive axis bonuses (existing)
  ├── mood/gut/threat modulation (existing)
  └── channel modulation (NEW)
        ├── reads CuriosityDrive.lastChannel() (nullable)
        ├── reads ChannelModulationConfig from SearchBudgetConfig (nullable)
        └── applies 5 rules with continuous gradient scaling
```

### Components Modified

#### 1. CuriosityDrive (cognition)

Add `lastChannel()` volatile accessor. During `evaluate()`, after computing `lastGaps()`, compute and cache the CuriosityChannel:

```java
private volatile @Nullable CuriosityChannel lastChannel;

// In evaluate():
this.lastChannel = channelComputer.compute(gaps, deprivationBias);

public @Nullable CuriosityChannel lastChannel() {
    return lastChannel;
}
```

CuriosityDrive gains a constructor dependency on CuriosityChannelComputer. No new module dependency — cognition already depends on mindmap-intelligence.

#### 2. ChannelModulationConfig (cognitive-api)

New record in `io.casehub.neocortex.cognitive`:

```java
public record ChannelModulationConfig(
    double specificDepthBoost,
    double specificBreadthPenalty,
    double diversiveBreadthBoost,
    double diversiveLateralityBoost,
    double diversiveConvergencePenalty,
    double deprivationConvergenceBoost,
    double lowComprehensionLateralityBoost,
    double highComprehensionConvergenceBoost
) {
    public static ChannelModulationConfig defaults() {
        return new ChannelModulationConfig(
            0.12, 0.08, 0.12, 0.10, 0.08, 0.10, 0.10, 0.10
        );
    }
}
```

Default magnitudes are comparable to existing drive bonuses (0.08–0.15 range).

#### 3. SearchBudgetConfig (cognitive-api)

Add nullable field:

```java
public record SearchBudgetConfig(
    // existing 10 fields...
    @Nullable ChannelModulationConfig channelModulation
) {
    // Update defaults() and constructor
}
```

When `channelModulation` is null, no channel modulation is applied — graceful degradation.

#### 4. SearchBudgetTickParticipant (cognition)

Add CuriosityDrive constructor dependency. After existing computation, apply channel modulation:

```java
private void applyChannelModulation(
        double[] budget, // [breadth, laterality, depth, convergence]
        CuriosityChannel channel,
        ChannelModulationConfig mod) {

    // Focus type modulation (discrete)
    if (channel.focus() == FocusType.SPECIFIC) {
        budget[2] += channel.gapProximity() * mod.specificDepthBoost();
        budget[0] -= channel.gapProximity() * mod.specificBreadthPenalty();
    } else { // DIVERSIVE
        budget[0] += mod.diversiveBreadthBoost();
        budget[1] += mod.diversiveLateralityBoost();
        budget[3] -= mod.diversiveConvergencePenalty();
    }

    // Hedonic type modulation (discrete)
    if (channel.hedonic() == HedonicType.D_TYPE) {
        budget[3] += mod.deprivationConvergenceBoost();
    } else if (channel.hedonic() == HedonicType.BLENDED) {
        budget[3] += mod.deprivationConvergenceBoost() * 0.5;
    }

    // Comprehension modulation (continuous gradient)
    budget[1] += (1.0 - channel.comprehension()) * mod.lowComprehensionLateralityBoost();
    budget[3] += channel.comprehension() * mod.highComprehensionConvergenceBoost();

    // Clamp all to [0, 1]
    for (int i = 0; i < budget.length; i++) {
        budget[i] = Math.max(0.0, Math.min(1.0, budget[i]));
    }
}
```

**Modulation rules summary:**

| Channel condition | Budget effect | Scaling |
|---|---|---|
| SPECIFIC focus + high gap proximity | +depth, −breadth | Continuous (gapProximity × magnitude) |
| DIVERSIVE focus | +breadth, +laterality, −convergence | Discrete (full magnitude) |
| D_TYPE hedonic | +convergence | Discrete (full magnitude; BLENDED at 50%) |
| Low comprehension | +laterality | Continuous ((1−comprehension) × magnitude) |
| High comprehension | +convergence | Continuous (comprehension × magnitude) |

The modulation is additive and applied after existing computation. All values clamped to [0,1] at the end.

#### 5. CognitiveDerivationEngine (cognitive-index)

New `deriveChannelModulationDefaults()` pathway following the existing linear-delta pattern:

```java
private ChannelModulationConfig deriveChannelModulationDefaults(
        Map<Pid5Facet, Double> facets, DispositionAxes axes) {

    double openness = computeOpenness(facets);           // existing composite
    double conscientiousness = computeConscientiousness(facets, axes); // existing composite
    double anxiousness = facetOrDefault(facets, ANXIOUSNESS);

    var d = ChannelModulationConfig.defaults();
    return new ChannelModulationConfig(
        d.specificDepthBoost() + (conscientiousness - 0.5) * 0.08,
        d.specificBreadthPenalty() + (conscientiousness - 0.5) * 0.06,
        d.diversiveBreadthBoost() + (openness - 0.5) * 0.10,
        d.diversiveLateralityBoost() + (openness - 0.5) * 0.08,
        d.diversiveConvergencePenalty() + (openness - 0.5) * 0.06,
        d.deprivationConvergenceBoost() + (anxiousness - 0.5) * 0.08,
        d.lowComprehensionLateralityBoost() + (openness - 0.5) * 0.08,
        d.highComprehensionConvergenceBoost() + (conscientiousness - 0.5) * 0.08
    );
}
```

**Personality mapping rationale:**

| Config field | Personality driver | Why |
|---|---|---|
| specificDepthBoost | conscientiousness | Methodical agents dig deeper when focused |
| specificBreadthPenalty | conscientiousness | Methodical agents narrow more when focused |
| diversiveBreadthBoost | openness | Open agents spread wider when scanning |
| diversiveLateralityBoost | openness | Open agents seek more lateral connections |
| diversiveConvergencePenalty | openness | Open agents resist premature closure |
| deprivationConvergenceBoost | anxiousness | Anxious agents feel stronger need to resolve gaps |
| lowComprehensionLateralityBoost | openness | Open agents seek different angles when confused |
| highComprehensionConvergenceBoost | conscientiousness | Methodical agents close out when understanding arrives |

Called from `deriveSearchBudgetDefaults()` — the channel modulation config is set on the SearchBudgetConfig it returns.

#### 6. CognitionCore (cognition)

Update `configureSearchBudget()` to pass CuriosityDrive to SearchBudgetTickParticipant constructor.

#### 7. SearchBudgetDescriptionGenerationPhase (mindmap-intelligence)

Update `buildUserPrompt()` to include channel tendency context derived from ChannelModulationConfig magnitudes (not live channel state):

```
Channel tendencies:
- Specific focus depth boost: 0.16 (above average — methodical deep diver)
- Diversive breadth boost: 0.17 (above average — broad scanner)
- Deprivation convergence: 0.14 (above average — anxious gap resolver)
```

The LLM uses these to generate prose like "When a specific question grips you, the world narrows and depth pulls you in. When scanning broadly, angles multiply faster than you can converge."

#### 8. SearchBudgetPromptSection (cognition)

No changes needed. The prompt section renders the tick-time SearchBudget values, which now include channel modulation effects. The values themselves carry the channel influence — no separate channel description needed at prompt-render time.

## Graceful Degradation

Every new dependency is nullable:

| Component | Null condition | Behaviour |
|---|---|---|
| CuriosityDrive.lastChannel() | No gaps computed yet, or CuriosityChannelComputer unavailable | Skip channel modulation entirely |
| SearchBudgetConfig.channelModulation() | Config not derived (no CognitiveDefaults for agent) | Skip channel modulation entirely |
| CuriosityDrive (in SearchBudgetTickParticipant) | CDI Instance not resolvable | Skip channel modulation entirely |

Existing behaviour is preserved when any nullable is absent.

## Module Dependencies

No new module dependencies. cognition already depends on mindmap-intelligence (where CuriosityChannelComputer lives). ChannelModulationConfig goes in cognitive-api (tier 1, zero deps).

## Testing

- **Unit tests for ChannelModulationConfig.defaults()** — verify sensible range [0, 0.25]
- **Unit tests for applyChannelModulation()** — verify each of the 5 rules in isolation: SPECIFIC+high gap → depth up/breadth down, DIVERSIVE → breadth+laterality up/convergence down, D_TYPE → convergence up, low comprehension → laterality up, high comprehension → convergence up
- **Clamping** — verify output stays in [0,1] with extreme inputs
- **Graceful degradation** — verify null channel or null config produces same output as before
- **CDE derivation** — verify high openness → larger diversive boosts, high conscientiousness → larger depth/convergence boosts
- **Integration** — verify CuriosityDrive.lastChannel() is populated after evaluate(), SearchBudgetTickParticipant reads it
- **Personality differentiation** — verify two agents with different openness/conscientiousness produce different budgets under same channel conditions

## References

- SearchBudgetTickParticipant — `cognition/src/main/java/.../cognition/SearchBudgetTickParticipant.java`
- CuriosityChannelComputer — `mindmap-intelligence/src/main/java/.../mindmap/intelligence/CuriosityChannelComputer.java`
- CuriosityDrive — `cognition/src/main/java/.../cognition/CuriosityDrive.java`
- CuriosityChannel — `cognition-api/src/main/java/.../cognition/CuriosityChannel.java`
- SearchBudgetConfig — `cognitive-api/src/main/java/.../cognitive/SearchBudgetConfig.java`
- CognitiveDerivationEngine — `cognitive-index/src/main/java/.../cognitive/index/CognitiveDerivationEngine.java`
- CognitionCore — `cognition/src/main/java/.../cognition/CognitionCore.java`
- SearchBudgetDescriptionGenerationPhase — `mindmap-intelligence/src/main/java/.../mindmap/intelligence/consolidation/SearchBudgetDescriptionGenerationPhase.java`
- casehubio/neocortex#846 — parent epic
- casehubio/neocortex#847 — this issue
