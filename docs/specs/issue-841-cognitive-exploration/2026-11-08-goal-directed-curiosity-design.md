# Goal-Directed Curiosity Design

**Issue:** casehubio/neocortex#848
**Parent:** casehubio/neocortex#846 (Cognitive exploration epic)
**Date:** 2026-11-08

## Problem

InformationGapDetector already accepts `activeGoalNodeIds` and applies a 1.5x boost for 1-hop GOAL_ADJACENT nodes. But CuriosityDrive passes `null` — goals never influence curiosity. An agent pursuing a specific goal should have higher curiosity about goal-adjacent topics and lower curiosity about unrelated ones.

Additionally, the GOAL_ADJACENT GapType masks the information-theoretic classification — a node that is both TIP_OF_TONGUE and goal-adjacent loses its TIP_OF_TONGUE identity.

## Design

### 1. Add `goalRelevance` field to InformationGapScore

Extend the record with a 9th field:

```java
public record InformationGapScore(
        String nodeId, String nodeName, String subgraphType,
        double gapIntensity, double entropy, int edgeCount,
        double confidence, GapType gapType,
        double goalRelevance) {  // NEW — [0.0, 1.0]
```

`goalRelevance` is 0.0 when no active goals exist or the node is beyond 3 hops. Graduated by BFS distance to the nearest active goal node:

| Distance | goalRelevance |
|----------|---------------|
| 1 hop    | 1.0           |
| 2 hops   | 0.7           |
| 3 hops   | 0.4           |
| 4+ / none| 0.0           |

These values match GoalRelevanceModulationFactor's existing graduated multiplier.

### 2. Remove GOAL_ADJACENT from GapType

The GapType enum drops GOAL_ADJACENT:

```java
public enum GapType {
    PARTIAL_KNOWLEDGE,
    TIP_OF_TONGUE,
    CONNECTED_UNKNOWN
}
```

`classifyGap()` always returns the information-theoretic type. Goal relevance is expressed solely via the continuous `goalRelevance` field. This fixes the information loss where GOAL_ADJACENT masked the underlying gap classification.

### 3. BFS goal proximity in InformationGapDetector

Replace the existing 1-hop adjacency check in `scoreNode()` with a BFS proximity computation. The algorithm mirrors GoalRelevanceModulationFactor's `shortestPathToGoal()`:

```java
private double computeGoalRelevance(MindMapNode node, String tenantId,
                                     Set<String> goalNodeIds) {
    // BFS from node, max 3 hops
    // distance 1 → 1.0, distance 2 → 0.7, distance 3 → 0.4
    // unreachable → 0.0
}
```

The goalRelevance multiplier replaces the existing flat 1.5x boost:

```java
// Before: if (gapType == GOAL_ADJACENT) gapIntensity *= 1.5
// After:
if (goalRelevance > 0.0) {
    gapIntensity = Math.min(1.0, gapIntensity * (1 + goalRelevance));
}
```

This means distance 1 gets a 2.0x boost, distance 2 gets 1.7x, distance 3 gets 1.4x — all capped at 1.0. The sorted output from `detectGaps()` naturally ranks goal-relevant gaps higher.

When `activeGoalNodeIds` is null or empty, `goalRelevance` is 0.0 for all gaps — no BFS is performed, preserving existing behavior.

### 4. Supplier-based goal injection into CuriosityDrive

Add a `Supplier<Set<String>>` parameter to CuriosityDrive's constructor:

```java
public CuriosityDrive(InformationGapDetector gapDetector,
                      CuriosityChannelComputer channelComputer,
                      double deprivationBias,
                      Supplier<Set<String>> activeGoalNodeIds) {
```

In `evaluate()`:

```java
var goals = activeGoalNodeIds.get();
var gaps = gapDetector.detectGaps(tenantId, goals.isEmpty() ? null : goals);
```

The wiring layer (CognitionCore / agency) provides a supplier that resolves active goal node IDs from MindMapStore's GOAL subgraph, filtering nodes with `property("status") == "active"`. This reuses the pattern from GoalRelevanceModulationFactor.getActiveGoals().

A no-goals supplier (`() -> Set.of()`) is the default for agents without goal integration.

### 5. CuriosityPromptSection renders goal relevance

When rendering gap descriptions, annotate goal-relevant gaps:

- `goalRelevance >= 0.7`: "closely related to your goal [goalName]"
- `goalRelevance >= 0.4`: "connected to your goal [goalName]"
- `goalRelevance > 0.0` but < 0.4: no special annotation

The goal name can be resolved from the nearest active goal node via the BFS path, or omitted if the resolution is too expensive — the goalRelevance value alone carries the signal.

### 6. No changes to CuriosityChannelComputer

CuriosityChannelComputer receives gap scores that already reflect goal bias in their `gapIntensity` values (via the multiplicative boost in InformationGapDetector). The channel derivation — hedonic type, comprehension, focus, gap proximity — operates on the same fields and naturally reflects the goal-biased ranking. No code changes needed.

## Modules Affected

| Module | Change |
|--------|--------|
| mindmap-intelligence | InformationGapScore (add field), InformationGapDetector (BFS, remove GOAL_ADJACENT), CuriosityPromptSection rendering |
| cognition | CuriosityDrive (add Supplier param, pass goals to detectGaps) |
| cognition (CognitionCore) | Wire supplier in configureCuriosity() |
| mindmap-intelligence tests | Update InformationGapScore constructors, test BFS proximity, test goal-biased ranking |
| cognition tests | Test CuriosityDrive with goal supplier |

## Testing Strategy

1. **InformationGapDetector BFS tests:** Verify graduated goalRelevance at distances 1, 2, 3, and beyond. Verify gapIntensity boost at each distance. Verify null/empty goals produces goalRelevance 0.0.
2. **GapType regression:** Verify GOAL_ADJACENT removal — all gaps get information-theoretic classification regardless of goal proximity.
3. **CuriosityDrive integration:** Verify goals flow from supplier → detectGaps → gap ranking. Verify no-goals supplier produces same behavior as before.
4. **Ranking verification:** Construct a graph where a lower-entropy gap is goal-adjacent and a higher-entropy gap is not — verify goal-adjacent gap ranks higher after boost.

## References

- InformationGapDetector.java — existing 1-hop check and GOAL_ADJACENCY_BOOST
- InformationGapScore.java — record to extend
- GoalRelevanceModulationFactor.java — BFS pattern and graduated multiplier values
- CuriosityDrive.java — null goal pass, constructor to extend
- CuriosityChannelComputer.java — no changes needed (consumes gapIntensity)
- CognitionCore.java:838-851 — curiosity wiring site
- Loewenstein (1994) — information-gap theory
- Golman & Loewenstein (2016) — entropy × attention weight
