# Goal-Directed Curiosity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #848 — Goal-directed curiosity: active goals bias information gap prioritisation
**Issue group:** #789, #846, #848

**Goal:** Wire active goal node IDs into the curiosity pipeline so goal-adjacent information gaps are boosted by BFS proximity, replacing the flat 1-hop GOAL_ADJACENT classification.

**Architecture:** Add `goalRelevance` field to InformationGapScore. Replace 1-hop GOAL_ADJACENT with multi-hop BFS proximity (max 3 hops, graduated multiplier). Inject a `Supplier<Set<String>>` into CuriosityDrive to supply active goal node IDs without coupling to MindMapStore. Remove GOAL_ADJACENT from GapType enum.

**Tech Stack:** Java 21, JUnit 5, AssertJ, InMemoryMindMapStore (tests)

## Global Constraints

- Java 21 source level, Java 26 JVM
- All commits reference #848: `Refs #848` or `Closes #848`
- TDD: failing test before implementation
- `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn` for all builds

---

## Batch 1: Core Model — goalRelevance field and BFS proximity

### Task 1: Add goalRelevance field to InformationGapScore and remove GOAL_ADJACENT

**Files:**
- Modify: `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/InformationGapScore.java`
- Modify: `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/InformationGapDetector.java`
- Modify: `mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/InformationGapDetectorTest.java`
- Modify: `cognition/src/test/java/io/casehub/neocortex/cognition/drive/CuriosityChannelComputerTest.java`
- Modify: `cognition/src/test/java/io/casehub/neocortex/cognition/prompt/ContinuumRenderingTest.java`
- Modify: `cognition/src/test/java/io/casehub/neocortex/cognition/RecurringThoughtIntegrationTest.java`

**Interfaces:**
- Produces: `InformationGapScore` record with 9 fields (adds `double goalRelevance` as 9th field)
- Produces: `GapType` enum with 3 values (removes `GOAL_ADJACENT`)

- [ ] **Step 1: Update InformationGapScore record — add goalRelevance, remove GOAL_ADJACENT**

Add `double goalRelevance` as the 9th field. Add validation `goalRelevance >= 0.0 && goalRelevance <= 1.0`. Remove `GOAL_ADJACENT` from `GapType` enum.

```java
public record InformationGapScore(
        String nodeId,
        String nodeName,
        String subgraphType,
        double gapIntensity,
        double entropy,
        int edgeCount,
        double confidence,
        GapType gapType,
        double goalRelevance) {

    public enum GapType {
        PARTIAL_KNOWLEDGE,
        TIP_OF_TONGUE,
        CONNECTED_UNKNOWN
    }

    public InformationGapScore {
        if (gapIntensity < 0.0 || gapIntensity > 1.0)
            throw new IllegalArgumentException("gapIntensity must be in [0,1], got " + gapIntensity);
        if (entropy < 0.0)
            throw new IllegalArgumentException("entropy must be non-negative");
        if (edgeCount < 0)
            throw new IllegalArgumentException("edgeCount must be non-negative");
        if (goalRelevance < 0.0 || goalRelevance > 1.0)
            throw new IllegalArgumentException("goalRelevance must be in [0,1], got " + goalRelevance);
    }
}
```

- [ ] **Step 2: Update InformationGapDetector — BFS proximity, graduated boost**

Replace `GOAL_ADJACENCY_BOOST` constant with `MAX_GOAL_DEPTH = 3`. Add `computeGoalRelevance()` BFS method. Update `scoreNode()` to use graduated boost `(1 + goalRelevance)` instead of flat 1.5x. Update `classifyGap()` to remove the GOAL_ADJACENT early return — always classify by information-theoretic type.

In `scoreNode()`, replace:

```java
// OLD — remove these lines:
InformationGapScore.GapType gapType = classifyGap(confidence, edgeCount, edges, goalNodeIds);
if (gapType == InformationGapScore.GapType.GOAL_ADJACENT) {
    gapIntensity = Math.min(1.0, gapIntensity * GOAL_ADJACENCY_BOOST);
}
return new InformationGapScore(
        node.id(), node.name(),
        node.subgraphType() != null ? node.subgraphType() : "unknown",
        gapIntensity, entropy, edgeCount, confidence, gapType);
```

With:

```java
// NEW:
InformationGapScore.GapType gapType = classifyGap(confidence, edgeCount);
double goalRelevance = computeGoalRelevance(node, tenantId, goalNodeIds);
if (goalRelevance > 0.0) {
    gapIntensity = Math.min(1.0, gapIntensity * (1 + goalRelevance));
}
return new InformationGapScore(
        node.id(), node.name(),
        node.subgraphType() != null ? node.subgraphType() : "unknown",
        gapIntensity, entropy, edgeCount, confidence, gapType, goalRelevance);
```

Update `classifyGap()` — remove `edges` and `goalNodeIds` parameters, remove GOAL_ADJACENT branch:

```java
private InformationGapScore.GapType classifyGap(double confidence, int edgeCount) {
    if (confidence >= TIP_OF_TONGUE_CONFIDENCE && edgeCount >= TIP_OF_TONGUE_MIN_EDGES) {
        return InformationGapScore.GapType.TIP_OF_TONGUE;
    }
    if (confidence < LOW_CONFIDENCE_FLOOR && edgeCount > 0) {
        return InformationGapScore.GapType.CONNECTED_UNKNOWN;
    }
    return InformationGapScore.GapType.PARTIAL_KNOWLEDGE;
}
```

Add BFS method (mirrors GoalRelevanceModulationFactor.shortestPathToGoal()):

```java
private static final int MAX_GOAL_DEPTH = 3;

private double computeGoalRelevance(MindMapNode node, String tenantId,
                                     @Nullable Set<String> goalNodeIds) {
    if (goalNodeIds == null || goalNodeIds.isEmpty()) return 0.0;
    if (goalNodeIds.contains(node.id())) return 1.0;

    Set<String> visited = new HashSet<>();
    visited.add(node.id());
    Set<String> currentLevel = Set.of(node.id());

    for (int depth = 1; depth <= MAX_GOAL_DEPTH; depth++) {
        Set<String> nextLevel = new HashSet<>();
        for (String nodeId : currentLevel) {
            List<MindMapEdge> edges = store.neighbors(nodeId, tenantId);
            for (MindMapEdge edge : edges) {
                String neighbor = edge.sourceNodeId().equals(nodeId)
                        ? edge.targetNodeId() : edge.sourceNodeId();
                if (goalNodeIds.contains(neighbor)) {
                    return switch (depth) {
                        case 1 -> 1.0;
                        case 2 -> 0.7;
                        case 3 -> 0.4;
                        default -> 0.0;
                    };
                }
                if (visited.add(neighbor)) nextLevel.add(neighbor);
            }
        }
        currentLevel = nextLevel;
        if (currentLevel.isEmpty()) break;
    }
    return 0.0;
}
```

Remove the `GOAL_ADJACENCY_BOOST` constant. Add `import java.util.HashSet;` (already present from existing code).

- [ ] **Step 3: Update all InformationGapScore constructor call sites**

Every test that constructs `InformationGapScore` directly needs the 9th `goalRelevance` parameter (use `0.0` for non-goal tests).

In `CuriosityChannelComputerTest.java`, update the helper method:

```java
private static InformationGapScore gap(String name, double intensity, double entropy,
                                        int edges, double confidence, GapType type) {
    return new InformationGapScore("id-" + name, name, "concept",
            intensity, entropy, edges, confidence, type, 0.0);
}
```

In `ContinuumRenderingTest.java` (~line 93):

```java
var gap = new InformationGapScore("node-1", "test-topic", "CONCEPT",
        0.6, 0.7, 3, 0.5, InformationGapScore.GapType.PARTIAL_KNOWLEDGE, 0.0);
```

In `RecurringThoughtIntegrationTest.java` (~line 95):

```java
var gap = new InformationGapScore("node-1", "spiral-topic", "CONCEPT",
        0.6, 0.7, 3, 0.5, InformationGapScore.GapType.PARTIAL_KNOWLEDGE, 0.0);
```

- [ ] **Step 4: Update InformationGapDetectorTest — fix goalAdjacentNode test, add BFS tests**

Replace the `goalAdjacentNode_boosted` test to verify the new graduated model. The node directly linked to a goal should now have `goalRelevance == 1.0` and its information-theoretic type (PARTIAL_KNOWLEDGE) instead of GOAL_ADJACENT:

```java
@Test
void goalAdjacentNode_graduatedRelevance() {
    String sgConcept = store.createSubgraph(
            new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
    String sgGoal = store.createSubgraph(
            new SubgraphInput("goals", SubgraphTypes.GOAL, null), TENANT);

    String goalNode = store.addNode(NodeInput.of("my-goal", sgGoal), TENANT);
    String gapNode = store.addNode(NodeInput.of("near-goal", sgConcept)
            .withConfidence(Confidence.inferred(0.4, NOW)), TENANT);
    store.addEdge(EdgeInput.of(goalNode, gapNode, "requires"), TENANT);

    var gaps = detector.detectGaps(TENANT, Set.of(goalNode));
    var nearGoal = gaps.stream()
            .filter(g -> g.nodeName().equals("near-goal"))
            .findFirst();
    assertThat(nearGoal).isPresent();
    assertThat(nearGoal.get().goalRelevance()).isEqualTo(1.0);
    assertThat(nearGoal.get().gapType())
            .isEqualTo(InformationGapScore.GapType.PARTIAL_KNOWLEDGE);
}
```

Add a test for 2-hop and 3-hop BFS proximity:

```java
@Test
void goalProximity_twoHops_reducedRelevance() {
    String sgConcept = store.createSubgraph(
            new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
    String sgGoal = store.createSubgraph(
            new SubgraphInput("goals", SubgraphTypes.GOAL, null), TENANT);

    String goalNode = store.addNode(NodeInput.of("my-goal", sgGoal), TENANT);
    String bridge = store.addNode(NodeInput.of("bridge", sgConcept)
            .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
    String farNode = store.addNode(NodeInput.of("two-hops-away", sgConcept)
            .withConfidence(Confidence.inferred(0.4, NOW)), TENANT);
    store.addEdge(EdgeInput.of(goalNode, bridge, "requires"), TENANT);
    store.addEdge(EdgeInput.of(bridge, farNode, "relates-to"), TENANT);

    var gaps = detector.detectGaps(TENANT, Set.of(goalNode));
    var farGap = gaps.stream()
            .filter(g -> g.nodeName().equals("two-hops-away"))
            .findFirst();
    assertThat(farGap).isPresent();
    assertThat(farGap.get().goalRelevance()).isEqualTo(0.7);
}

@Test
void goalProximity_threeHops_minimalRelevance() {
    String sgConcept = store.createSubgraph(
            new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
    String sgGoal = store.createSubgraph(
            new SubgraphInput("goals", SubgraphTypes.GOAL, null), TENANT);

    String goalNode = store.addNode(NodeInput.of("my-goal", sgGoal), TENANT);
    String b1 = store.addNode(NodeInput.of("bridge-1", sgConcept)
            .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
    String b2 = store.addNode(NodeInput.of("bridge-2", sgConcept)
            .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
    String farNode = store.addNode(NodeInput.of("three-hops-away", sgConcept)
            .withConfidence(Confidence.inferred(0.4, NOW)), TENANT);
    store.addEdge(EdgeInput.of(goalNode, b1, "requires"), TENANT);
    store.addEdge(EdgeInput.of(b1, b2, "relates-to"), TENANT);
    store.addEdge(EdgeInput.of(b2, farNode, "relates-to"), TENANT);

    var gaps = detector.detectGaps(TENANT, Set.of(goalNode));
    var farGap = gaps.stream()
            .filter(g -> g.nodeName().equals("three-hops-away"))
            .findFirst();
    assertThat(farGap).isPresent();
    assertThat(farGap.get().goalRelevance()).isEqualTo(0.4);
}

@Test
void noGoals_zeroRelevanceForAll() {
    String sgId = store.createSubgraph(
            new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
    String nodeA = store.addNode(NodeInput.of("topic-a", sgId)
            .withConfidence(Confidence.inferred(0.4, NOW)), TENANT);
    String nodeB = store.addNode(NodeInput.of("topic-b", sgId)
            .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
    store.addEdge(EdgeInput.of(nodeA, nodeB, "relates-to"), TENANT);

    var gaps = detector.detectGaps(TENANT, null);
    assertThat(gaps).allMatch(g -> g.goalRelevance() == 0.0);
}

@Test
void goalBoost_reshapesRanking() {
    String sgConcept = store.createSubgraph(
            new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
    String sgGoal = store.createSubgraph(
            new SubgraphInput("goals", SubgraphTypes.GOAL, null), TENANT);

    String goalNode = store.addNode(NodeInput.of("my-goal", sgGoal), TENANT);

    // Low-entropy gap: near goal
    String nearGoal = store.addNode(NodeInput.of("near-goal", sgConcept)
            .withConfidence(Confidence.stated(0.7, NOW)), TENANT);
    String ctx1 = store.addNode(NodeInput.of("ctx-1", sgConcept)
            .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
    store.addEdge(EdgeInput.of(nearGoal, ctx1, "relates-to"), TENANT);
    store.addEdge(EdgeInput.of(goalNode, nearGoal, "requires"), TENANT);

    // High-entropy gap: far from goal
    String farGap = store.addNode(NodeInput.of("far-gap", sgConcept)
            .withConfidence(Confidence.inferred(0.45, NOW)), TENANT);
    String ctx2 = store.addNode(NodeInput.of("ctx-2", sgConcept)
            .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
    store.addEdge(EdgeInput.of(farGap, ctx2, "relates-to"), TENANT);

    var gapsWithGoals = detector.detectGaps(TENANT, Set.of(goalNode));
    var nearGapScore = gapsWithGoals.stream()
            .filter(g -> g.nodeName().equals("near-goal")).findFirst();
    var farGapScore = gapsWithGoals.stream()
            .filter(g -> g.nodeName().equals("far-gap")).findFirst();

    assertThat(nearGapScore).isPresent();
    assertThat(farGapScore).isPresent();
    // Goal boost should lift near-goal above far-gap despite lower raw entropy
    assertThat(nearGapScore.get().gapIntensity())
            .isGreaterThan(farGapScore.get().gapIntensity());
}
```

- [ ] **Step 5: Run tests to verify**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence,cognition -am -q`

Expected: all tests pass including new BFS tests and updated constructor call sites.

- [ ] **Step 6: Commit**

```bash
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/InformationGapScore.java \
       mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/InformationGapDetector.java \
       mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/InformationGapDetectorTest.java \
       cognition/src/test/java/io/casehub/neocortex/cognition/drive/CuriosityChannelComputerTest.java \
       cognition/src/test/java/io/casehub/neocortex/cognition/prompt/ContinuumRenderingTest.java \
       cognition/src/test/java/io/casehub/neocortex/cognition/RecurringThoughtIntegrationTest.java
git commit -m "feat(#848): add goalRelevance to InformationGapScore with BFS proximity

Replace flat 1-hop GOAL_ADJACENT classification with graduated BFS
proximity scoring (1.0/0.7/0.4 at 1/2/3 hops). Remove GOAL_ADJACENT
from GapType — information-theoretic classification always preserved.

Refs #848"
```

## Batch 2: Drive Wiring — supplier injection and prompt rendering

### Task 2: Add goal supplier to CuriosityDrive

**Files:**
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/drive/CuriosityDrive.java`
- Modify: `cognition/src/test/java/io/casehub/neocortex/cognition/drive/CuriosityQualityTest.java`

**Interfaces:**
- Consumes: `InformationGapDetector.detectGaps(tenantId, Set<String>)` from Task 1
- Produces: `CuriosityDrive(InformationGapDetector, CuriosityChannelComputer, double, Supplier<Set<String>>)` constructor

- [ ] **Step 1: Write failing test — CuriosityDrive with goal supplier**

Add a test to `CuriosityQualityTest.java` that constructs CuriosityDrive with a goal supplier and verifies goal-adjacent gaps are boosted:

```java
@Test
void goalDirectedCuriosity_boostsGoalAdjacentGaps() {
    String sgId = ensureSubgraph();
    String sgGoal = store.createSubgraph(
            new SubgraphInput("goals", SubgraphTypes.GOAL, null), TENANT);
    String goalNode = store.addNode(NodeInput.of("learn-rust", sgGoal)
            .withProperties(java.util.Map.of("status", "active")), TENANT);

    addGapNode("rust-ownership", 0.4, 2);
    // link gap to goal
    var ownershipNode = store.search(
            io.casehub.neocortex.mindmap.MindMapQuery.of(TENANT, 10)
                    .withSubgraphId(sgId)).stream()
            .filter(n -> n.name().equals("rust-ownership"))
            .findFirst().orElseThrow();
    store.addEdge(EdgeInput.of(goalNode, ownershipNode.id(), "requires"), TENANT);

    addGapNode("unrelated-topic", 0.4, 2);

    var drive = new CuriosityDrive(detector, channelComputer, 0.5,
            () -> Set.of(goalNode));
    var intensity = drive.evaluate("agent-1", TENANT);

    assertThat(intensity.intensity()).isGreaterThan(0.0);
    // Goal-adjacent gap should rank first in lastGaps
    var gaps = drive.lastGaps();
    assertThat(gaps.getFirst().nodeName()).isEqualTo("rust-ownership");
    assertThat(gaps.getFirst().goalRelevance()).isEqualTo(1.0);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -am -Dtest=CuriosityQualityTest#goalDirectedCuriosity_boostsGoalAdjacentGaps -q`

Expected: FAIL — CuriosityDrive constructor doesn't accept 4th parameter.

- [ ] **Step 3: Update CuriosityDrive — add Supplier parameter**

```java
package io.casehub.neocortex.cognition.drive;

import io.casehub.neocortex.mindmap.intelligence.CuriosityChannelComputer;
import io.casehub.neocortex.mindmap.intelligence.InformationGapDetector;
import io.casehub.neocortex.mindmap.intelligence.InformationGapScore;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class CuriosityDrive implements DriveSource {

    private static final int                       TOP_N    = 5;
    private final        InformationGapDetector    gapDetector;
    private final        CuriosityChannelComputer  channelComputer;
    private final        double                    deprivationBias;
    private final        Supplier<Set<String>>     activeGoalNodeIds;
    private volatile     List<InformationGapScore> lastGaps = List.of();
    private volatile     CuriosityChannel          lastChannel;

    public CuriosityDrive(InformationGapDetector gapDetector,
                          CuriosityChannelComputer channelComputer,
                          double deprivationBias,
                          Supplier<Set<String>> activeGoalNodeIds) {
        this.gapDetector        = gapDetector;
        this.channelComputer    = channelComputer;
        this.deprivationBias    = deprivationBias;
        this.activeGoalNodeIds  = activeGoalNodeIds;
    }

    public CuriosityDrive(InformationGapDetector gapDetector,
                          CuriosityChannelComputer channelComputer,
                          double deprivationBias) {
        this(gapDetector, channelComputer, deprivationBias, Set::of);
    }

    @Override
    public DriveIntensity evaluate(String agentId, String tenantId) {
        var goals = activeGoalNodeIds.get();
        var gaps = gapDetector.detectGaps(tenantId, goals.isEmpty() ? null : goals);
        this.lastGaps    = gaps;
        this.lastChannel = channelComputer.compute(gaps, deprivationBias);

        if (gaps.isEmpty()) {
            return new DriveIntensity(DriveAxis.CURIOSITY, 0.0, "no knowledge gaps");
        }

        var topGaps = gaps.stream().limit(TOP_N).toList();
        double intensity = topGaps.stream()
                                  .mapToDouble(InformationGapScore::gapIntensity)
                                  .average().orElse(0.0);
        intensity = Math.clamp(intensity, 0.0, 1.0);

        String trigger = topGaps.stream()
                                .map(this::formatTrigger)
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("unknown gaps");

        return new DriveIntensity(DriveAxis.CURIOSITY, intensity, trigger);
    }

    private String formatTrigger(InformationGapScore g) {
        String base = g.nodeName() + " (" + g.gapType().name().toLowerCase().replace('_', ' ') + ")";
        if (g.goalRelevance() >= 0.7) return base + " [goal-relevant]";
        return base;
    }

    public List<InformationGapScore> lastGaps() {
        return lastGaps;
    }

    public CuriosityChannel lastChannel() {
        return lastChannel;
    }
}
```

Note the backward-compatible 3-arg constructor that defaults to `Set::of`.

- [ ] **Step 4: Run tests to verify all pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -am -q`

Expected: all tests pass — existing 3-arg constructor callers unchanged, new test passes.

- [ ] **Step 5: Commit**

```bash
git add cognition/src/main/java/io/casehub/neocortex/cognition/drive/CuriosityDrive.java \
       cognition/src/test/java/io/casehub/neocortex/cognition/drive/CuriosityQualityTest.java
git commit -m "feat(#848): inject goal supplier into CuriosityDrive

Add Supplier<Set<String>> activeGoalNodeIds to CuriosityDrive.
Goals flow to InformationGapDetector.detectGaps() to activate BFS
proximity scoring. Backward-compatible 3-arg constructor defaults
to Set::of. Trigger string annotates goal-relevant gaps.

Refs #848"
```

### Task 3: Update CuriosityPromptSection and CLAUDE.md

**Files:**
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/prompt/CuriosityPromptSection.java`
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: `InformationGapScore.goalRelevance()` from Task 1, `CuriosityDrive.lastGaps()` from Task 2

- [ ] **Step 1: Update CuriosityPromptSection to annotate goal-relevant gaps**

In CuriosityPromptSection, where gap names are rendered (~line 120 and any other gap rendering), add goal relevance context. Find the gap rendering logic and add after the gap name:

```java
// When rendering gap names, append goal context
if (gap.goalRelevance() >= 0.7) {
    sb.append(" (closely related to an active goal)");
} else if (gap.goalRelevance() >= 0.4) {
    sb.append(" (connected to an active goal)");
}
```

This is a light touch — the exact insertion point depends on how gaps are rendered. Read the full CuriosityPromptSection to find all gap name rendering sites.

- [ ] **Step 2: Update CLAUDE.md**

Add `goalRelevance` field documentation to InformationGapScore in the mindmap-intelligence module description. Update the GapType documentation to remove GOAL_ADJACENT. Update CuriosityDrive description to mention the `Supplier<Set<String>>` constructor parameter.

- [ ] **Step 3: Run full build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -pl mindmap-intelligence,cognition -am -q`

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add cognition/src/main/java/io/casehub/neocortex/cognition/prompt/CuriosityPromptSection.java \
       CLAUDE.md
git commit -m "feat(#848): render goal relevance in CuriosityPromptSection

Annotate goal-relevant gaps in curiosity rendering: 'closely related
to an active goal' for goalRelevance >= 0.7, 'connected to an active
goal' for >= 0.4. Update CLAUDE.md with goalRelevance field docs.

Closes #848"
```

## References

- `specs/issue-841-cognitive-exploration/2026-11-08-goal-directed-curiosity-design.md` — design spec
- `mindmap-intelligence/src/main/java/.../InformationGapDetector.java` — core detector to modify
- `mindmap-intelligence/src/main/java/.../InformationGapScore.java` — record to extend
- `cognitive-index/src/main/java/.../GoalRelevanceModulationFactor.java:76-101` — BFS pattern source
- `cognition/src/main/java/.../CuriosityDrive.java` — drive to inject supplier into
- `cognition/src/main/java/.../CuriosityPromptSection.java` — prompt rendering
- `cognition/src/main/java/.../CognitionCore.java:838-851` — wiring site (agency layer)
- GitHub #848 — focal issue
- GitHub #846 — parent epic
