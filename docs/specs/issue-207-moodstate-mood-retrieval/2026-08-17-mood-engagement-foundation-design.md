# MoodState Type + Mood-Modulated Retrieval + Conversational Engagement Scoring

**Date:** 2026-08-17
**Issues:** casehubio/neocortex#207, casehubio/neocortex#208
**Branch:** issue-207-moodstate-mood-retrieval

## Motivation

CaseHub's autonomous agent patterns (blocks Mood §2.3, StrategyLearning §2.6) require two foundation additions in neocortex memory-api:

1. **Dynamic emotional state** — a PAD (Pleasure-Arousal-Dominance) model alongside static personality, with mood-modulated memory retrieval
2. **Conversational engagement scoring** — standardised per-interaction outcome measurements for social interactions

Both are durable agent state that must survive shutdown, restart, and crash recovery. Memory is the persistence layer for agent state that outlives a single execution — not "things the agent remembers" but "agent state that outlives a process boundary." This is the unifying principle for all memory-api domain event types.

## §1 MoodState Type

**Package:** `io.casehub.neocortex.memory.mood`

### MoodState record

```java
public record MoodState(
    String agentId,
    String tenantId,
    double pleasure,    // [-1.0, 1.0]
    double arousal,     // [-1.0, 1.0]
    double dominance,   // [-1.0, 1.0]
    String cause,       // what triggered this mood shift
    String turnId,      // nullable — interaction that caused the shift
    Map<String, String> metadata
)
```

Constructor validates:
- `agentId`, `tenantId` required (non-null)
- All three PAD axes in [-1.0, 1.0]
- `cause` required (non-null, non-blank)
- `metadata` required, defensively copied

### Storage

Mood shifts are stored as an event log via `CaseMemoryStore` with `MemoryDomain("mood")` (D2, D3). Each shift is a separate event — current mood is derived by reading the most recent event for an agent. This gives mood trajectory for emotional continuity analysis, trend detection, and correlation with engagement outcomes.

The ExperienceEvent sealed hierarchy (`Observation | Action | Outcome`) is unchanged. Mood appraisal is derived internal state, not an agent experience.

### MoodEvents converter

```java
public final class MoodEvents {
    public static final MemoryDomain DOMAIN = new MemoryDomain("mood");

    public static MemoryInput toMemoryInput(MoodState state)
}
```

Converts `MoodState` to `MemoryInput`:
- `entityId` = `agentId`
- `domain` = `DOMAIN`
- `text` = `cause`
- `attributes` = PAD values + turnId serialised via `MoodAttributeKeys`

### MoodAttributeKeys

```java
public final class MoodAttributeKeys {
    public static final String PLEASURE = "pleasure";
    public static final String AROUSAL = "arousal";
    public static final String DOMINANCE = "dominance";
    public static final String TURN_ID = "turn-id";
}
```

### Concurrent writes

Mood state is inherently approximate. Concurrent writes are handled by the append-only event log with last-write-wins by store-assigned timestamp (D11). No vector clock or single-writer constraint.

## §2 MoodBaseline and Decay

### MoodBaseline record

```java
public record MoodBaseline(
    double pleasure,    // [-1.0, 1.0]
    double arousal,     // [-1.0, 1.0]
    double dominance    // [-1.0, 1.0]
)
```

Constructor validates all three axes in [-1.0, 1.0].

This is per-agent configuration, not a stored event. It represents the emotional resting point that mood decays toward — set at agent creation time as part of character design. Not derived from `AgentDisposition`, which uses vocabulary-registrar terms (`List<DispositionValue>` with `String term, double weight`) that have no deterministic mapping to PAD floats (D5, D8).

Supplied by the agent creator (e.g., from eidos agent descriptor metadata or external config). Helper utilities may suggest PAD values from personality descriptions, but the baseline is a deliberate configuration choice.

### MoodDecay utility

```java
public final class MoodDecay {
    public static MoodState decay(MoodState current, MoodBaseline baseline,
                                   Duration elapsed, Duration timeConstant)
}
```

Exponential decay per axis (D9):

```
axis_new = axis_current + (axis_baseline - axis_current) * (1 - e^(-elapsed/timeConstant))
```

- Returns a new `MoodState` with decayed PAD values, same `agentId`, `tenantId`, cause="decay", no turnId
- `timeConstant` controls decay speed per agent — high τ for "emotionally sticky" characters, low τ for "emotionally reactive" ones
- Single time constant for all three axes (asymmetric per-axis decay deferred)
- Matches the exponential decay pattern already used by `TemporalDecay` in the CBR decorator chain

Edge cases:
- Zero elapsed → returns current unchanged
- Negative elapsed → treated as zero
- Already at baseline → returns baseline values

## §3 Mood-Modulated Retrieval

**Package:** `io.casehub.neocortex.memory.mood`

### MoodModulatedRetrieval utility

```java
public final class MoodModulatedRetrieval {
    public static List<Memory> reweight(List<Memory> memories,
            PersonalityWeights weights, MoodState currentMood,
            double moodInfluence, Instant now)
}
```

Extends the scoring formula from `PersonalityWeightedRetrieval` (D6, D10):

**Base score** (existing): `recencyDecay × importance × domainWeight`

**Mood factor** (new):
1. Read PAD attributes from `memory.attributes()` via `MoodAttributeKeys`
2. If any PAD attribute is present, compute alignment:
   `alignment = 1.0 - (euclideanDistance(memoryPAD, currentMoodPAD) / sqrt(12))`
   where `sqrt(12)` is the maximum distance in the [-1,1]³ cube
3. `moodFactor = 1.0 + moodInfluence × (alignment - 0.5)`
4. Memories without PAD attributes: `moodFactor = 1.0` (unaffected)

**Final score**: `recencyDecay × importance × domainWeight × moodFactor`

**Parameters:**
- `moodInfluence` in [0.0, 1.0] — controls bias strength. 0.0 = no mood effect (identical to `PersonalityWeightedRetrieval`), 1.0 = full effect
- Missing PAD dimensions on a memory are treated as 0.0 (neutral) for distance computation

**Scope:** `CaseMemoryStore` retrieval only. CBR retrieval (`CbrCaseMemoryStore`) is not mood-modulated — CBR is structured feature-vector similarity search where mood-congruent recall bias has no psychological justification (D10).

### Memory PAD annotation convention

Memory producers annotate memories with PAD values at ingestion time using `MoodAttributeKeys.PLEASURE`, `MoodAttributeKeys.AROUSAL`, `MoodAttributeKeys.DOMINANCE` as attribute keys on `MemoryInput`. This is a convention, not an enforcement — memories without PAD annotation are simply not mood-modulated.

LLM-assisted annotation at ingestion time makes this practical: an enrichment step can estimate PAD values from the memory text.

## §4 Conversational Engagement Scoring

**Package:** `io.casehub.neocortex.memory.engagement`

### EngagementEvent record

```java
public record EngagementEvent(
    String agentId,
    String otherAgentId,
    String tenantId,
    String caseId,
    String turnId,
    String description,
    Double importance,
    Map<String, String> metadata,
    Boolean responded,
    Long responseTimeMs,
    Integer responseLength,
    Double sentimentShift,
    Integer reactionCount,
    Boolean continued
)
```

Per-interaction engagement measurement (D4, D7). One event per agent message, recording how that specific message was received.

Constructor validates:
- `agentId`, `otherAgentId`, `tenantId`, `description` required (non-null, non-blank)
- `turnId` required (non-null, non-blank) — must link to the action being evaluated
- `agentId != otherAgentId` (same pattern as `RelationshipEvent`)
- `affectShift` in [-1.0, 1.0] if present
- `confidence` in [0.0, 1.0] if present
- `metadata` required, defensively copied

All signal fields are nullable — not every platform supports every signal. An event with only `responded=true` and everything else null is valid.

### Temporal decoupling

Engagement signals are temporally decoupled from the action they evaluate. The agent sends a message at T=0; whether the user responds, how quickly, and whether the conversation continues can only be measured later. Engagement events are recorded asynchronously and reference the evaluated interaction via `turnId`.

### EngagementEvents converter

```java
public final class EngagementEvents {
    public static final MemoryDomain DOMAIN = new MemoryDomain("engagement");

    public static MemoryInput toMemoryInput(EngagementEvent event)
}
```

Converts `EngagementEvent` to `MemoryInput`:
- `entityId` = `agentId`
- `domain` = `DOMAIN`
- `text` = `description`
- `attributes` = signal fields + otherAgentId + turnId serialised via `EngagementAttributeKeys`

### EngagementAttributeKeys

```java
public final class EngagementAttributeKeys {
    public static final String OTHER_AGENT = "other-agent";
    public static final String TURN_ID = "turn-id";
    public static final String RESPONDED = "responded";
    public static final String RESPONSE_TIME_MS = "response-time-ms";
    public static final String RESPONSE_LENGTH = "response-length";
    public static final String SENTIMENT_SHIFT = "sentiment-shift";
    public static final String REACTION_COUNT = "reaction-count";
    public static final String CONTINUED = "continued";
}
```

### CDI integration (memory module)

**`EngagementRecorded`** — CDI event fired after storage:
```java
public record EngagementRecorded(EngagementEvent event, String memoryId)
```

**`EngagementStream`** — `@ApplicationScoped` fire-and-forget wrapper:
```java
public class EngagementStream {
    public String record(EngagementEvent event)
    public EngagementStoreResult recordAll(List<EngagementEvent> events)
}
```

Follows the `ExperienceStream` pattern: converts via `EngagementEvents.toMemoryInput()`, stores via `CaseMemoryStore`, fires `EngagementRecorded` CDI event synchronously. `SecurityException` propagates; other store failures caught and logged.

**`EngagementStoreResult`** / **`EngagementStoreFailure`** — batch result types following `ExperienceStoreResult` / `ExperienceStoreFailure` pattern.

## §5 Module Placement

All new types in **memory-api** (pure Java, zero dependencies):

| Package | Types |
|---------|-------|
| `io.casehub.neocortex.memory.mood` | `MoodState`, `MoodBaseline`, `MoodDecay`, `MoodEvents`, `MoodAttributeKeys`, `MoodModulatedRetrieval` |
| `io.casehub.neocortex.memory.engagement` | `EngagementEvent`, `EngagementEvents`, `EngagementAttributeKeys` |

CDI wiring in **memory** (runtime module):

| Package | Types |
|---------|-------|
| `io.casehub.neocortex.memory.engagement` | `EngagementRecorded`, `EngagementStream`, `EngagementStoreResult`, `EngagementStoreFailure` |

No new SPI methods on `CaseMemoryStore` or `CbrCaseMemoryStore`. All types flow through existing `store(MemoryInput)`.

No new modules. No new Maven artifacts. No Flyway migrations.

## §6 Testing

Unit tests in **memory-api** `src/test/`:

| Test class | What it covers |
|-----------|---------------|
| `MoodStateTest` | Constructor validation: PAD bounds, cause required, defensive copy, agentId/tenantId required |
| `MoodBaselineTest` | Constructor validation: PAD bounds |
| `MoodDecayTest` | Exponential convergence to baseline, time constant effect, zero/negative elapsed, extreme PAD values, already-at-baseline |
| `MoodEventsTest` | Round-trip: `MoodState` → `toMemoryInput()` → verify domain, attributes, text |
| `MoodModulatedRetrievalTest` | PAD-annotated memories get mood-boosted; unannotated memories unaffected; `moodInfluence=0.0` matches `PersonalityWeightedRetrieval`; high alignment boosts score; low alignment suppresses; missing PAD dimensions default to 0.0 |
| `EngagementEventTest` | Constructor validation: bounds, required fields, self-referential rejection, nullable signals, defensive copy |
| `EngagementEventsTest` | Round-trip: `EngagementEvent` → `toMemoryInput()` → verify domain, attributes |

Integration tests in **memory** `src/test/`:

| Test class | What it covers |
|-----------|---------------|
| `EngagementStreamTest` | CDI wiring: event stored, `EngagementRecorded` fired, batch `recordAll` with partial failure |

No contract tests — no new SPI methods.

## §7 What This Does NOT Include

- **Appraisal logic** — how interaction events map to PAD shifts. That belongs in the blocks Mood pattern, not the foundation type.
- **Aggregate engagement metrics** — response rate, trends over time. `TrendAnalyzer` already computes aggregates from per-event data.
- **PAD annotation at ingestion time** — a `CaseEnrichmentStep` implementation for LLM-assisted PAD annotation is a natural follow-up but not in scope.
- **Mood-modulated CBR retrieval** — scoped out by D10; CBR is analytical, not episodic.
- **Asymmetric per-axis decay** — single time constant for all axes; per-axis constants deferred.
- **PAD baseline derivation from personality** — no deterministic mapping exists from CaseHub disposition vocabulary to PAD floats; baseline is explicit configuration.

## References

- Russell, J. A. (1980). "A circumplex model of affect." *Journal of Personality and Social Psychology*, 39(6), 1161–1178. — foundational PAD dimensional model
- Bower, G. H. (1981). "Mood and Memory." *American Psychologist*, 36(2), 129–148. — mood-congruent memory recall
- REMT — "Realtime Editable Memory Topology" (*Frontiers in AI*, 2026) — mood index for retrieval bias
- DAM-LLM — "Dynamic Affective Memory Management" (2025, arXiv:2510.27418) — affect-modulated memory architecture
- Chain-of-Emotion Architecture (PMC, 2024) — pre-response emotion appraisal
- "Self-Learning Agents Enhanced by Multi-level Reflection" (EMNLP 2025) — per-action, per-episode, per-strategy reflection tiers requiring engagement signals
- Reflexion — Shinn et al. (NeurIPS 2023) — verbal reinforcement learning from interaction outcomes
- ACT-R memory activation model — exponential decay (recency × frequency), basis for D9 decay formula
