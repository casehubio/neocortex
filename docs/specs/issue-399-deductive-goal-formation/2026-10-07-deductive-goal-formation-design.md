# Deductive Goal Formation — Design Spec

**Issue:** casehubio/neocortex#399
**Date:** 2026-10-07
**Scale:** M | **Complexity:** High

## Problem

Character goals are currently either prescribed (registered via `GoalProposalOrchestrator.registerGoals()`) or formed from drive state via per-axis `DriveGoalFormationStrategy`. Neither path supports situational reasoning — forming goals from the intersection of personality, beliefs, memories, and emotional state.

Example: an ENTJ character with greed, a belief about an inheritance will, and financial desperation should form the goal "eliminate benefactor to inherit" — not because a single drive axis triggers it, but because the combination of personality + knowledge + need produces it.

## Design

### New SPI: `DeductiveGoalFormationStrategy`

A holistic goal formation interface in `cognition-api`, separate from the per-axis `DriveGoalFormationStrategy`. Called once per tick with full cognitive context.

```java
// cognition-api: io.casehub.neocortex.cognition.goal
@FunctionalInterface
public interface DeductiveGoalFormationStrategy {
    List<DeductiveGoalProposal> propose(DeductiveFormationContext context);
}
```

### Context Record: `DeductiveFormationContext`

Assembled by `GoalProposalOrchestrator` from available stores. All fields beyond agentId/tenantId are nullable or empty-list safe — the strategy degrades gracefully when stores are unavailable.

```java
// cognition-api: io.casehub.neocortex.cognition.goal
public record DeductiveFormationContext(
    String agentId,
    String tenantId,
    DriveProfile driveProfile,
    @Nullable DispositionAxes disposition,
    List<String> beliefs,
    List<String> recentMemories,
    @Nullable MoodState currentMood,
    List<AgentGoal> existingGoals,
    int remainingCapacity
) {
    public DeductiveFormationContext {
        Objects.requireNonNull(agentId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(driveProfile);
        beliefs = beliefs != null ? List.copyOf(beliefs) : List.of();
        recentMemories = recentMemories != null ? List.copyOf(recentMemories) : List.of();
        existingGoals = existingGoals != null ? List.copyOf(existingGoals) : List.of();
    }
}
```

**Belief retrieval:** The orchestrator queries MindMap for nodes in the agent's COGNITIVE/GOAL subgraphs, bounded by count and recency. The exact retrieval strategy is an implementation detail tuned by prompt size constraints — not a design-level decision.

**Memory retrieval:** The orchestrator scans CaseMemoryStore for recent experience memories, bounded by count. Same tuning principle.

### Proposal Record: `DeductiveGoalProposal`

Multi-axis proposal carrying the contributing drives map. Converts to `DriveGoalProposal` for pipeline compatibility.

```java
// cognition-api: io.casehub.neocortex.cognition.goal
public record DeductiveGoalProposal(
    String goalName,
    String goalDescription,
    String reasoning,
    Map<DriveAxis, Double> driveContributions,
    @Nullable GoalPriority suggestedPriority,
    @Nullable Map<String, String> attributes
) {
    public DeductiveGoalProposal {
        Objects.requireNonNull(goalName);
        Objects.requireNonNull(goalDescription);
        Objects.requireNonNull(reasoning);
        driveContributions = driveContributions != null
            ? Map.copyOf(driveContributions) : Map.of();
        attributes = attributes != null ? Map.copyOf(attributes) : null;
    }

    public DriveAxis primaryAxis() {
        return driveContributions.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(DriveAxis.CURIOSITY);
    }

    public double primaryIntensity() {
        return driveContributions.values().stream()
            .mapToDouble(Double::doubleValue).max().orElse(0.5);
    }

    public DriveGoalProposal toDriveGoalProposal() {
        var attrs = new HashMap<String, String>();
        attrs.put("source", "deductive");
        attrs.put("reasoning", reasoning);
        if (attributes != null) attrs.putAll(attributes);
        return new DriveGoalProposal(
            primaryAxis(), goalName, goalDescription,
            "deductive: " + reasoning,
            primaryIntensity(), suggestedPriority, Map.copyOf(attrs));
    }
}
```

### Orchestrator Integration

`GoalProposalOrchestrator` gains an optional `@Nullable DeductiveGoalFormationStrategy` dependency. The deductive evaluation runs **before** per-axis mappers in `doTick()`:

```
doTick() flow:
  1. Check cooldown                              (existing)
  2. Get DriveProfile                            (existing)
  3. evaluateRelevance — abandonments            (existing)
  4. evaluateDeductive(agentId, tenantId,         NEW
       profile, descriptor, remainingCapacity)
     → assembles DeductiveFormationContext
     → calls strategy.propose(context)
     → converts to DriveGoalProposal via toDriveGoalProposal()
     → deducts from remainingCapacity
  5. evaluateMappers — per-axis                  (existing, reduced capacity)
  6. evaluateCrossAxis                           (existing)
  7. evaluateEscalation                          (existing)
  8. Sort, cap, return                           (existing)
```

**Context assembly in the orchestrator** requires `Instance<MindMapStore>` and `Instance<CaseMemoryStore>` — both use `Instance<>` graceful degradation (isResolvable check). When stores aren't available, the context is assembled with empty beliefs/memories.

**Disposition** comes from `CognitiveDefaultsRegistry.forAgentOrDefaults(agentId).descriptor().disposition()` — the same path used by `AppraisalTickParticipant` (#433).

### LLM Implementation: `LlmDeductiveGoalFormationStrategy`

In `cognition` module. Follows the `LlmAppraisalStrategy` pattern:

- Constructor: `AgentProvider agentProvider`
- Builds system prompt explaining the deductive reasoning task
- Builds user message from `DeductiveFormationContext` fields
- Invokes LLM via `AgentProvider.invoke(AgentSessionConfig.of(system, user))`
- Parses JSON array response into `List<DeductiveGoalProposal>`
- Returns empty list on any failure (graceful degradation)

**System prompt:** Instructs the LLM to act as the character's subconscious goal formation process. Given personality, drives, beliefs, memories, mood, and existing goals — reason about what goals this character would naturally form. Only propose novel goals not in existingGoals.

**Response JSON schema:**
```json
[{
  "goalName": "short-kebab-identifier",
  "description": "what the goal is",
  "reasoning": "why this character would form this goal",
  "driveContributions": { "DOMINANCE": 0.9, "COMPETENCE": 0.3 }
}]
```

### Default Bean: `NoOpDeductiveGoalFormationStrategy`

`@DefaultBean` in `cognition` module — returns `List.of()`. Active when no `AgentProvider` is available. Same pattern as `NoOpAppraisalStrategy`.

### CDI Wiring

`CognitionDefaultBeans` (or equivalent producer) provides:
- `LlmDeductiveGoalFormationStrategy` when `AgentProvider` is available
- `NoOpDeductiveGoalFormationStrategy` otherwise

`GoalProposalOrchestrator` accepts the strategy as a nullable constructor parameter (existing pattern).

## Module Impact

| Module | Change |
|--------|--------|
| `cognition-api` | Add `DeductiveGoalFormationStrategy`, `DeductiveFormationContext`, `DeductiveGoalProposal` |
| `cognition` | Add `LlmDeductiveGoalFormationStrategy`, `NoOpDeductiveGoalFormationStrategy`; update `GoalProposalOrchestrator` with new dependency + `evaluateDeductive()` |

No changes to `mindmap-api`, `memory-api`, or any store implementation — the orchestrator queries stores via existing SPIs.

## Testing

1. **`DeductiveGoalProposalTest`** — `toDriveGoalProposal()` conversion, `primaryAxis()` selection, `primaryIntensity()`, immutability
2. **`LlmDeductiveGoalFormationStrategyTest`** — stub `AgentProvider`, verify prompt includes disposition/beliefs/memories/drives/mood, verify JSON parsing, verify graceful fallback on failure/malformed JSON
3. **`GoalProposalOrchestratorTest` extension** — mock strategy, verify deductive proposals appear in Changes, verify capacity reduction, verify deductive runs before per-axis, verify NoOp when strategy absent
4. **`GoalFormationEmergenceTest` extension** (follow-up) — once #399 lands, add deductive scenarios to the CAPS emergence test suite from #402

## References

- `cognition-api/.../goal/DriveGoalFormationStrategy.java` — existing per-axis SPI
- `cognition-api/.../goal/DriveGoalFormationContext.java` — existing per-axis context
- `cognition-api/.../goal/DriveGoalProposal.java` — existing proposal type
- `cognition/.../goal/GoalProposalOrchestrator.java` — orchestrator, evaluateMappers()
- `cognition/.../appraisal/LlmAppraisalStrategy.java` — LLM-backed strategy pattern
- `cognition/.../appraisal/AppraisalTickParticipant.java:63` — disposition extraction from CognitiveDefaults
- `cognition/.../core/CognitionCore.java` — composition root, promptSections()
- casehubio/neocortex#402 — GoalFormationEmergenceTest (CAPS emergence tests)
- casehubio/neocortex#433 — DispositionAxes in AppraisalContext (disposition wiring pattern)
- decisions.md — D1-D5
