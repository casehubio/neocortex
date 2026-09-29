# GoalRevision Consumption — Decay-Signal Lifecycle Transitions

**Issue:** blocks#300
**Parent epic:** blocks#298 (Cognitive emotion architecture)
**Date:** 2026-09-29

## Problem

`GoalPrioritizationPhase` (neocortex) sets `decay-signal` properties on MindMap goal nodes when their confidence drops below thresholds. `CognitiveGoalOrchestrator` (blocks) reads these signals and produces `GoalRevision` records. But nothing consumes those records — the data flow is broken between steps 2 and 4:

1. **GoalPrioritizationPhase** sets `decay-signal` = "dormant" | "abandon" on linked goal nodes ✅
2. **CognitiveGoalOrchestrator.checkDecaySignal()** reads signals → `GoalRevision` records → `pendingRevisions()` ✅
3. **Gap:** No consumer reads `pendingRevisions()` to transition eidos goal lifecycle state ❌
4. **GoalResolutionPhase.sync()** clears `decay-signal` after importing eidos state — but only `NoOpGoalLifecycleProvider` exists (returns empty map) ❌
5. **Gap:** No `GoalLifecycleProvider` impl bridges eidos back to neocortex ❌

## Design

Three changes across three repos close the loop:

### 1. eidos-api: `updateGoalLifecycleState` default method on AgentRegistry

```java
default void updateGoalLifecycleState(String agentId, String tenancyId,
                                       String goalName, GoalLifecycleState newState) {
    findById(agentId, tenancyId).ifPresent(descriptor -> {
        var updatedGoals = descriptor.goals().stream()
            .map(g -> g.name().equals(goalName)
                ? g.toBuilder().lifecycleState(newState).build()
                : g)
            .toList();
        register(descriptor.toBuilder().goals(updatedGoals).build());
    });
}
```

The default method does read-modify-write via existing `findById` + `register`. Implementations may override with optimized paths:

- **InMemoryAgentRegistry**: `computeIfPresent` on the ConcurrentHashMap — same read-modify-write but avoids the vocabulary re-validation in `register()`.
- **JpaAgentRegistry**: direct UPDATE on the `AgentGoalEntity` table — atomic, avoids the DELETE + flush + clear + persist cycle in `register()`.

Optimized overrides are optional follow-up work — the default is correct.

### 2. blocks: Revision consumer in SocialAvatarCognition

After `core.tick()` completes in `SocialAvatarCognition.tick()`, consume pending revisions:

```java
@Override
public void tick(String agentId, String tenantId, Set<String> activeSubjects) {
    var descriptor = resolveDescriptor(agentId, tenantId);
    core.tick(agentId, tenantId, descriptor, (aid, tid) -> activeSubjects);
    consumeGoalRevisions(agentId, tenantId);  // NEW
}

private void consumeGoalRevisions(String agentId, String tenantId) {
    if (cognitiveGoals == null || agentRegistry.isEmpty()) return;

    for (var revision : cognitiveGoals.pendingRevisions(agentId, tenantId)) {
        if (revision.eidosGoalName() == null) continue;

        GoalLifecycleState newState = switch (revision.decaySignal()) {
            case "dormant" -> GoalLifecycleState.DORMANT;
            case "abandon" -> GoalLifecycleState.ABANDONED;
            default -> null;
        };
        if (newState == null) continue;

        try {
            agentRegistry.get().updateGoalLifecycleState(
                agentId, tenantId, revision.eidosGoalName(), newState);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                "Failed to transition goal '" + revision.eidosGoalName() + "'", e);
        }
    }
}
```

**Key behaviors:**
- Skips revisions without `eidosGoalName` — standalone goals don't have an eidos counterpart
- Maps "dormant" → `DORMANT`, "abandon" → `ABANDONED`; ignores unknown signals
- Error isolation per revision — one failure doesn't block others
- Idempotent — re-consuming the same revision calls `updateGoalLifecycleState` with the same state, which is a no-op when the goal is already in that state

### 3. blocks: EidosGoalLifecycleProvider

A `GoalLifecycleProvider` implementation in `blocks-core` that reads from `AgentRegistry`:

```java
@ApplicationScoped
public class EidosGoalLifecycleProvider implements GoalLifecycleProvider {

    @Inject Instance<AgentRegistry> agentRegistry;

    @Override
    public Map<String, String> getLifecycleStates(String agentId, String tenantId) {
        if (!agentRegistry.isResolvable()) return Map.of();

        return agentRegistry.get().findById(agentId, tenantId)
            .map(descriptor -> descriptor.goals().stream()
                .filter(g -> g.lifecycleState() != null)
                .collect(Collectors.toMap(
                    AgentGoal::name,
                    g -> g.lifecycleState().name().toLowerCase())))
            .orElse(Map.of());
    }
}
```

This displaces `NoOpGoalLifecycleProvider` (@DefaultBean @Priority(0)) via CDI priority. When `GoalResolutionPhase.sync()` runs, it calls this provider, gets the eidos lifecycle states, and clears `decay-signal` from nodes whose eidos state has been updated.

**Status mapping:** `GoalLifecycleState.DORMANT.name().toLowerCase()` → "dormant", which matches the status strings used by `GoalPrioritizationPhase` for standalone goals. `GoalResolutionPhase.sync()` writes this as the node's `status` property and removes `decay-signal`.

## Data Flow (Complete Loop)

```
GoalPrioritizationPhase          CognitiveGoalOrchestrator
(neocortex, @Priority 38)        (blocks, TERMINAL tick)
        │                                │
        │ sets decay-signal              │ reads decay-signal
        │ on MindMap node                │ produces GoalRevision
        ▼                                ▼
   ┌──────────┐                   ┌──────────────┐
   │ MindMap   │                  │ pendingRevi-  │
   │ goal node │                  │ sions()       │
   └──────────┘                   └──────┬───────┘
        ▲                                │
        │                                │ SocialAvatarCognition
        │                                │ consumes after tick
        │                                ▼
        │                         ┌──────────────┐
        │                         │ AgentRegistry │
        │                         │ .updateGoal-  │
        │                         │ LifecycleState│
        │                         └──────┬───────┘
        │                                │
        │                                │ stores new GoalLifecycleState
        │                                ▼
        │                         ┌──────────────┐
        │   GoalResolutionPhase   │ EidosGoal-   │
        │   .sync() reads via     │ Lifecycle-   │
        │◄────────────────────────│ Provider     │
        │   clears decay-signal   └──────────────┘
        │   sets status
```

## Idempotency and Convergence

The `decay-signal` property on a MindMap node persists until `GoalResolutionPhase.sync()` clears it. Between setting and clearing, `CognitiveGoalOrchestrator` will produce the same `GoalRevision` on every tick. The consumer in `SocialAvatarCognition` will call `updateGoalLifecycleState` with the same state — this is safe because:

1. The default method reads the current state, sees the goal is already in DORMANT/ABANDONED, rebuilds an identical descriptor, and registers it (no-op effect)
2. Once `GoalResolutionPhase.sync()` runs and clears `decay-signal`, the revision disappears from subsequent ticks

The convergence window depends on the consolidation scheduler interval (typically 1+ minute idle guard). During this window, the consumer makes redundant but harmless calls.

## Testing Strategy

| Test | What it verifies |
|------|-----------------|
| `AgentRegistry.updateGoalLifecycleState` default method test | Goal state transitions correctly, unknown goal names are no-ops, missing agent is no-op |
| `SocialAvatarCognition` revision consumption test | "dormant"→DORMANT, "abandon"→ABANDONED, null eidosGoalName skipped, error isolation |
| `EidosGoalLifecycleProvider` test | Returns lifecycle states from registry, graceful degradation when registry absent |
| Integration: full loop | Set decay-signal → tick → verify eidos state changed → sync → verify decay-signal cleared |

## Scope Per Repo

| Repo | Changes | Files |
|------|---------|-------|
| **eidos** | `updateGoalLifecycleState` default method on `AgentRegistry` | `api/.../AgentRegistry.java` |
| **eidos** | Tests for the default method | `api/src/test/.../AgentRegistrySpiTest.java` |
| **blocks** | Revision consumption in `SocialAvatarCognition.tick()` | `blocks-core/.../SocialAvatarCognition.java` |
| **blocks** | `EidosGoalLifecycleProvider` | `blocks-core/.../goal/EidosGoalLifecycleProvider.java` (new) |
| **blocks** | Tests for consumption and provider | `blocks-core/src/test/...` |

No neocortex changes required — `GoalResolutionPhase.sync()` and `GoalLifecycleProvider` SPI are already in place.

## References

- `eidos-api/AgentRegistry.java` — SPI to extend
- `eidos-api/AgentGoal.java` — `lifecycleState` field, `toBuilder()` API
- `eidos-api/GoalLifecycleState.java` — DORMANT and ABANDONED enum values
- `blocks-core/CognitiveGoalOrchestrator.java:76` — `pendingRevisions()` accessor
- `blocks-core/CognitiveGoalOrchestrator.java:190` — `checkDecaySignal()` producer
- `blocks-core/SocialAvatarCognition.java:130` — `tick()` method where consumption hooks in
- `neocortex/GoalResolutionPhase.java:305` — `sync()` method that clears decay-signal
- `neocortex/GoalLifecycleProvider.java` — SPI that EidosGoalLifecycleProvider implements
- `neocortex/GoalPrioritizationPhase.java:142` — `decay()` method that sets the signal
- blocks#298 — parent epic
- blocks#299 — SocialAvatarCognition CDI wiring (prerequisite, CLOSED)
