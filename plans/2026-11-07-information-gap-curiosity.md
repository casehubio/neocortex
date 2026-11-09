# Information-Gap Curiosity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #596 — Information-gap curiosity: felt deprivation as problem-solving fuel (Loewenstein 1994)
**Issue group:** #596, #841

**Goal:** Replace retention-based curiosity intensity with Loewenstein's
information-gap model and render curiosity through four literature-grounded
channels (Litman I/D, Silvia appraisal, Berlyne focus, Loewenstein proximity),
with proportional rendering relative to baseline, competing drives, and
trajectory.

**Architecture:** Three layers of change. (1) A new `InformationGapDetector`
in mindmap-intelligence scans MindMap nodes for partial-knowledge sweet spots
— moderate confidence, connected to known concepts — and computes per-node gap
scores using Golman & Loewenstein entropy. (2) `CuriosityDrive` in cognition
is rewritten to consume information-gap signals instead of memory-retention
statistics, producing a richer `DriveIntensity` with four-channel metadata.
(3) A new `CuriosityDescriptionGenerationPhase` consolidation phase generates
LLM prose through four channels (hedonic, comprehension, focus, gap proximity),
crystallised to MindMap COGNITIVE subgraph like DriveDescriptionGenerationPhase
and SearchBudgetDescriptionGenerationPhase. The prompt section reads crystallised
prose or falls back to programmatic four-channel rendering with proportional
context (baseline deviation, drive rank, momentum).

**Tech Stack:** Java 21, Quarkus CDI, MindMap SPI, CaseMemoryStore, cognition
tick pipeline, consolidation phases, CognitiveDefaults derivation.

## Global Constraints

- Java 21 language level, Java 26 JVM
- No new module creation — changes live in existing modules (cognition-api,
  cognition, mindmap-api, mindmap-intelligence, cognitive-api, cognitive-index)
- Follow existing patterns: records for value types, @ApplicationScoped for
  CDI beans, @Priority for consolidation phase ordering
- CuriosityConfig lives in mindmap-api (tier 1, zero CDI deps)
- CognitiveDefaults derivation lives in cognitive-index
- Consolidation phases live in mindmap-intelligence
- Tick participants and prompt sections live in cognition
- All commits reference #596

---

## Batch 1: Information Gap Detection Foundation

### Task 1: InformationGapScore record and InformationGapDetector

The core gap detection logic — scans MindMap nodes for partial-knowledge
sweet spots per Loewenstein (1994) and computes entropy-based gap scores
per Golman & Loewenstein (2016).

**Files:**
- Create: `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/InformationGapScore.java`
- Create: `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/InformationGapDetector.java`
- Test: `mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/InformationGapDetectorTest.java`

**Interfaces:**
- Consumes: `MindMapStore.search(MindMapQuery)`, `MindMapStore.getEdges(nodeId, tenantId)`, `MindMapStore.listSubgraphs(tenantId)`, `MindMapNode.confidence()`, `MindMapNode.traits()`
- Produces:
  - `InformationGapScore(String nodeId, String nodeName, String subgraphType, double gapIntensity, double entropy, int edgeCount, double confidence, GapType gapType)` — per-node gap score
  - `GapType` enum: `PARTIAL_KNOWLEDGE`, `TIP_OF_TONGUE`, `CONNECTED_UNKNOWN`, `GOAL_ADJACENT`
  - `InformationGapDetector.detectGaps(String tenantId, @Nullable Set<String> activeGoalNodeIds) → List<InformationGapScore>` — sorted by gapIntensity descending

- [ ] **Step 1: Write the InformationGapScore record**

```java
package io.casehub.neocortex.mindmap.intelligence;

public record InformationGapScore(
        String nodeId,
        String nodeName,
        String subgraphType,
        double gapIntensity,
        double entropy,
        int edgeCount,
        double confidence,
        GapType gapType) {

    public enum GapType {
        PARTIAL_KNOWLEDGE,
        TIP_OF_TONGUE,
        CONNECTED_UNKNOWN,
        GOAL_ADJACENT
    }

    public InformationGapScore {
        if (gapIntensity < 0.0 || gapIntensity > 1.0)
            throw new IllegalArgumentException("gapIntensity must be in [0,1]");
    }
}
```

- [ ] **Step 2: Write failing tests for InformationGapDetector**

```java
package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InformationGapDetectorTest {

    private InMemoryMindMapStore store;
    private InformationGapDetector detector;
    private static final String TENANT = "test-tenant";

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        detector = new InformationGapDetector(store);
    }

    @Test
    void emptyGraph_returnsNoGaps() {
        var gaps = detector.detectGaps(TENANT, null);
        assertTrue(gaps.isEmpty());
    }

    @Test
    void highConfidenceNode_noGap() {
        // fully known node — no curiosity
        store.addNode(NodeInput.of("known", SubgraphTypes.CONCEPT)
                .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), TENANT);
        var gaps = detector.detectGaps(TENANT, null);
        assertTrue(gaps.stream().noneMatch(g -> g.nodeId().contains("known")));
    }

    @Test
    void moderateConfidenceWithEdges_partialKnowledge() {
        // partial knowledge sweet spot: moderate confidence + connections
        var nodeA = store.addNode(NodeInput.of("partial", SubgraphTypes.CONCEPT)
                .withConfidence(new Confidence(ConfidenceOrigin.INFERRED, 0.4, null)), TENANT);
        var nodeB = store.addNode(NodeInput.of("related", SubgraphTypes.CONCEPT)
                .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.8, null)), TENANT);
        store.addEdge(nodeA.id(), nodeB.id(), "relates-to", TENANT);

        var gaps = detector.detectGaps(TENANT, null);
        assertFalse(gaps.isEmpty());
        var gap = gaps.getFirst();
        assertEquals(InformationGapScore.GapType.PARTIAL_KNOWLEDGE, gap.gapType());
        assertTrue(gap.gapIntensity() > 0.3, "partial knowledge should have significant gap intensity");
    }

    @Test
    void zeroConfidenceNoEdges_noGap() {
        // completely unknown, no connections — don't know what you don't know
        store.addNode(NodeInput.of("unknown", SubgraphTypes.CONCEPT)
                .withConfidence(new Confidence(ConfidenceOrigin.SPECULATED, 0.05, null)), TENANT);
        var gaps = detector.detectGaps(TENANT, null);
        assertTrue(gaps.isEmpty() || gaps.getFirst().gapIntensity() < 0.1);
    }

    @Test
    void nearCompleteKnowledge_tipOfTongue() {
        // near-complete: high confidence but one missing connection
        var node = store.addNode(NodeInput.of("almost-known", SubgraphTypes.CONCEPT)
                .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.7, null)), TENANT);
        for (int i = 0; i < 5; i++) {
            var other = store.addNode(NodeInput.of("ctx-" + i, SubgraphTypes.CONCEPT)
                    .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), TENANT);
            store.addEdge(node.id(), other.id(), "relates-to", TENANT);
        }

        var gaps = detector.detectGaps(TENANT, null);
        var tipGaps = gaps.stream()
                .filter(g -> g.nodeName().equals("almost-known"))
                .toList();
        assertFalse(tipGaps.isEmpty());
        assertEquals(InformationGapScore.GapType.TIP_OF_TONGUE, tipGaps.getFirst().gapType());
    }

    @Test
    void goalAdjacentNode_boosted() {
        var goalNode = store.addNode(NodeInput.of("my-goal", SubgraphTypes.GOAL), TENANT);
        var gapNode = store.addNode(NodeInput.of("near-goal", SubgraphTypes.CONCEPT)
                .withConfidence(new Confidence(ConfidenceOrigin.INFERRED, 0.4, null)), TENANT);
        store.addEdge(goalNode.id(), gapNode.id(), "requires", TENANT);

        var gaps = detector.detectGaps(TENANT, Set.of(goalNode.id()));
        var goalAdjacent = gaps.stream()
                .filter(g -> g.nodeName().equals("near-goal"))
                .findFirst();
        assertTrue(goalAdjacent.isPresent());
        assertEquals(InformationGapScore.GapType.GOAL_ADJACENT, goalAdjacent.get().gapType());
    }

    @Test
    void gapsSortedByIntensityDescending() {
        store.addNode(NodeInput.of("low-gap", SubgraphTypes.CONCEPT)
                .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.6, null)), TENANT);
        var highGap = store.addNode(NodeInput.of("high-gap", SubgraphTypes.CONCEPT)
                .withConfidence(new Confidence(ConfidenceOrigin.INFERRED, 0.35, null)), TENANT);
        var ctx = store.addNode(NodeInput.of("context", SubgraphTypes.CONCEPT)
                .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), TENANT);
        store.addEdge(highGap.id(), ctx.id(), "relates-to", TENANT);

        var gaps = detector.detectGaps(TENANT, null);
        if (gaps.size() >= 2) {
            assertTrue(gaps.get(0).gapIntensity() >= gaps.get(1).gapIntensity());
        }
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest=InformationGapDetectorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure — InformationGapDetector not defined

- [ ] **Step 4: Implement InformationGapDetector**

```java
package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Detects information gaps in the MindMap knowledge graph using
 * Loewenstein's (1994) information-gap theory. Curiosity peaks at
 * partial knowledge — moderate confidence with connections to known
 * concepts — and is absent at the extremes (know nothing, know everything).
 * <p>
 * Gap intensity follows Golman & Loewenstein (2016): entropy × attention weight,
 * where attention weight derives from edge connectivity to the broader graph.
 */
public class InformationGapDetector {

    private static final double LOW_CONFIDENCE_FLOOR = 0.1;
    private static final double HIGH_CONFIDENCE_CEILING = 0.85;
    private static final double TIP_OF_TONGUE_CONFIDENCE = 0.6;
    private static final int TIP_OF_TONGUE_MIN_EDGES = 4;
    private static final double GOAL_ADJACENCY_BOOST = 1.5;

    private final MindMapStore store;

    public InformationGapDetector(MindMapStore store) {
        this.store = store;
    }

    public List<InformationGapScore> detectGaps(String tenantId,
                                                  @Nullable Set<String> activeGoalNodeIds) {
        var subgraphs = store.listSubgraphs(tenantId);
        if (subgraphs.isEmpty()) return List.of();

        var gaps = new ArrayList<InformationGapScore>();

        for (MindMapSubgraph sg : subgraphs) {
            if (SubgraphTypes.TYPE_SYSTEM.equals(sg.type())) continue;

            var nodes = store.search(MindMapQuery.of(tenantId, 200)
                    .withSubgraphId(sg.id()));

            for (MindMapNode node : nodes) {
                var score = scoreNode(node, tenantId, activeGoalNodeIds);
                if (score != null && score.gapIntensity() >= 0.1) {
                    gaps.add(score);
                }
            }
        }

        gaps.sort(Comparator.comparingDouble(InformationGapScore::gapIntensity).reversed());
        return List.copyOf(gaps);
    }

    private @Nullable InformationGapScore scoreNode(MindMapNode node, String tenantId,
                                                     @Nullable Set<String> goalNodeIds) {
        double confidence = node.confidence() != null ? node.confidence().value() : 0.5;
        if (confidence >= HIGH_CONFIDENCE_CEILING) return null;

        var edges = store.getEdges(node.id(), tenantId);
        int edgeCount = edges.size();

        if (confidence < LOW_CONFIDENCE_FLOOR && edgeCount == 0) return null;

        double entropy = computeEntropy(confidence);
        double attentionWeight = Math.log1p(edgeCount) / Math.log1p(10);
        double gapIntensity = Math.min(1.0, entropy * Math.min(attentionWeight, 1.0));

        InformationGapScore.GapType gapType = classifyGap(
                confidence, edgeCount, node, goalNodeIds);

        if (gapType == InformationGapScore.GapType.GOAL_ADJACENT) {
            gapIntensity = Math.min(1.0, gapIntensity * GOAL_ADJACENCY_BOOST);
        }

        return new InformationGapScore(
                node.id(), node.name(),
                node.subgraphType() != null ? node.subgraphType() : "unknown",
                gapIntensity, entropy, edgeCount, confidence, gapType);
    }

    /**
     * Binary entropy H(p) = -p*log2(p) - (1-p)*log2(1-p).
     * Maximum at p=0.5, zero at p=0 and p=1.
     */
    static double computeEntropy(double confidence) {
        if (confidence <= 0.0 || confidence >= 1.0) return 0.0;
        return -(confidence * log2(confidence) + (1 - confidence) * log2(1 - confidence));
    }

    private InformationGapScore.GapType classifyGap(
            double confidence, int edgeCount, MindMapNode node,
            @Nullable Set<String> goalNodeIds) {
        if (goalNodeIds != null && !goalNodeIds.isEmpty()) {
            // check if any edge connects to a goal node
            var edges = store.getEdges(node.id(),
                    node.subgraphId() != null ? node.subgraphId() : "");
            // re-check via the edges we already have
        }
        if (goalNodeIds != null) {
            var nodeEdges = store.getEdges(node.id(),
                    /* tenantId already used in caller */ "");
            // simplified: if the node itself is goal-adjacent we catch it via
            // the detectGaps caller passing goal node IDs
        }

        if (confidence >= TIP_OF_TONGUE_CONFIDENCE && edgeCount >= TIP_OF_TONGUE_MIN_EDGES) {
            return InformationGapScore.GapType.TIP_OF_TONGUE;
        }
        if (confidence < LOW_CONFIDENCE_FLOOR && edgeCount > 0) {
            return InformationGapScore.GapType.CONNECTED_UNKNOWN;
        }
        return InformationGapScore.GapType.PARTIAL_KNOWLEDGE;
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}
```

Note: The goal-adjacency classification needs the tenantId threaded properly.
The implementation above is a draft — the actual code must pass the tenantId
through `scoreNode` and check edges against the `activeGoalNodeIds` set.
The implementer should refine `classifyGap` to receive `tenantId` and use
`store.getEdges(nodeId, tenantId)` to check whether any edge target is in
the `goalNodeIds` set.

- [ ] **Step 5: Run tests and iterate until green**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest=InformationGapDetectorTest`

- [ ] **Step 6: Commit**

```bash
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/InformationGapScore.java
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/InformationGapDetector.java
git add mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/InformationGapDetectorTest.java
git commit -m "feat(#596): add InformationGapDetector — Loewenstein entropy-based gap scoring

Refs #596"
```

---

### Task 2: Rewrite CuriosityDrive to use information gaps

Replace retention-based intensity with information-gap-based intensity.
CuriosityDrive reads from InformationGapDetector (fed by consolidation)
instead of MemoryHygieneOrchestrator.

**Files:**
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/drive/CuriosityDrive.java`
- Test: `cognition/src/test/java/io/casehub/neocortex/cognition/drive/CuriosityDriveTest.java` (create or modify)

**Interfaces:**
- Consumes: `InformationGapDetector.detectGaps(tenantId, goalNodeIds)`, `MindMapStore` (for active goal lookup)
- Produces: `DriveIntensity(CURIOSITY, intensity, trigger)` where intensity is derived from top gap scores and trigger describes the gap

- [ ] **Step 1: Write failing test for gap-based CuriosityDrive**

```java
@Test
void curiosityIntensity_basedOnInformationGaps() {
    // Setup: add nodes with partial knowledge sweet spot
    var nodeA = store.addNode(NodeInput.of("partial-knowledge", SubgraphTypes.CONCEPT)
            .withConfidence(new Confidence(ConfidenceOrigin.INFERRED, 0.4, null)), TENANT);
    var nodeB = store.addNode(NodeInput.of("context", SubgraphTypes.CONCEPT)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), TENANT);
    store.addEdge(nodeA.id(), nodeB.id(), "relates-to", TENANT);

    var detector = new InformationGapDetector(store);
    var drive = new CuriosityDrive(detector);

    var intensity = drive.evaluate("agent-1", TENANT);
    assertEquals(DriveAxis.CURIOSITY, intensity.axis());
    assertTrue(intensity.intensity() > 0.0);
    assertTrue(intensity.trigger().contains("partial-knowledge"));
}

@Test
void noGaps_zeroCuriosity() {
    var detector = new InformationGapDetector(store);
    var drive = new CuriosityDrive(detector);

    var intensity = drive.evaluate("agent-1", TENANT);
    assertEquals(0.0, intensity.intensity());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=CuriosityDriveTest`
Expected: FAIL — constructor signature mismatch

- [ ] **Step 3: Rewrite CuriosityDrive**

Replace `MemoryHygieneOrchestrator` dependency with `InformationGapDetector`.
Intensity = mean of top-N gap scores (capped at 5). Trigger = comma-separated
names of top gaps.

```java
public class CuriosityDrive implements DriveSource {

    private static final int TOP_N = 5;
    private final InformationGapDetector gapDetector;
    private volatile List<InformationGapScore> lastGaps = List.of();

    public CuriosityDrive(InformationGapDetector gapDetector) {
        this.gapDetector = gapDetector;
    }

    @Override
    public DriveIntensity evaluate(String agentId, String tenantId) {
        var gaps = gapDetector.detectGaps(tenantId, null);
        this.lastGaps = gaps;

        if (gaps.isEmpty()) {
            return new DriveIntensity(DriveAxis.CURIOSITY, 0.0, "no knowledge gaps");
        }

        var topGaps = gaps.stream().limit(TOP_N).toList();
        double intensity = topGaps.stream()
                .mapToDouble(InformationGapScore::gapIntensity)
                .average().orElse(0.0);
        intensity = Math.clamp(intensity, 0.0, 1.0);

        String trigger = topGaps.stream()
                .map(g -> g.nodeName() + " (" + g.gapType().name().toLowerCase().replace('_', ' ') + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("unknown gaps");

        return new DriveIntensity(DriveAxis.CURIOSITY, intensity, trigger);
    }

    public List<InformationGapScore> lastGaps() {
        return lastGaps;
    }
}
```

- [ ] **Step 4: Update CognitionDefaultBeans producer for CuriosityDrive**

The CDI producer that creates `CuriosityDrive` needs to inject
`InformationGapDetector` instead of `MemoryHygieneOrchestrator`. Find
the producer in `CognitionDefaultBeans` and update the dependency.

- [ ] **Step 5: Run tests and verify green**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=CuriosityDriveTest`

- [ ] **Step 6: Run full build to check nothing breaks**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition,mindmap-intelligence`

- [ ] **Step 7: Commit**

```bash
git add cognition/src/main/java/io/casehub/neocortex/cognition/drive/CuriosityDrive.java
git add cognition/src/test/java/io/casehub/neocortex/cognition/drive/CuriosityDriveTest.java
git commit -m "feat(#596): rewrite CuriosityDrive to use information-gap detection

Replace retention-based intensity (MemoryHygieneOrchestrator) with
Loewenstein information-gap model (InformationGapDetector). Intensity
now derives from entropy × attention weight over partial-knowledge
nodes in MindMap.

Refs #596"
```

---

## Batch 2: Four-Channel Curiosity Rendering

### Task 3: CuriosityChannel record and channel computation

Value types for the four Loewenstein/Litman/Silvia/Berlyne channels and
a `CuriosityChannelComputer` that derives channel values from gap data +
personality + graph structure.

**Files:**
- Create: `cognition-api/src/main/java/io/casehub/neocortex/cognition/drive/CuriosityChannel.java`
- Create: `../../mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/CuriosityChannelComputer.java`
- Test: `cognition/src/test/java/io/casehub/neocortex/cognition/drive/CuriosityChannelComputerTest.java`

**Interfaces:**
- Consumes: `List<InformationGapScore>` from CuriosityDrive, `PersonalityWeights` from CognitiveDefaults, `DriveProfile` from DriveOrchestrator
- Produces:
  - `CuriosityChannel(HedonicType hedonic, double comprehension, FocusType focus, double gapProximity)`
  - `HedonicType` enum: `I_TYPE` (interest), `D_TYPE` (deprivation), `BLENDED`
  - `FocusType` enum: `SPECIFIC`, `DIVERSIVE`
  - `CuriosityChannelComputer.compute(List<InformationGapScore>, PersonalityWeights, @Nullable DriveProfile) → CuriosityChannel`

- [ ] **Step 1: Write CuriosityChannel record in cognition-api**

```java
package io.casehub.neocortex.cognition.drive;

public record CuriosityChannel(
        HedonicType hedonic,
        double comprehension,
        FocusType focus,
        double gapProximity) {

    public enum HedonicType { I_TYPE, D_TYPE, BLENDED }
    public enum FocusType { SPECIFIC, DIVERSIVE }

    public CuriosityChannel {
        if (comprehension < 0.0 || comprehension > 1.0)
            throw new IllegalArgumentException("comprehension must be in [0,1]");
        if (gapProximity < 0.0 || gapProximity > 1.0)
            throw new IllegalArgumentException("gapProximity must be in [0,1]");
    }
}
```

- [ ] **Step 2: Write failing tests for CuriosityChannelComputer**

Test cases:
- High openness personality → I_TYPE hedonic
- High neuroticism personality → D_TYPE hedonic
- Single dominant gap → SPECIFIC focus
- Many scattered gaps → DIVERSIVE focus
- Mean confidence ~0.5 → peak gap proximity (Loewenstein non-linear)
- All high confidence → low gap proximity

- [ ] **Step 3: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=CuriosityChannelComputerTest`

- [ ] **Step 4: Implement CuriosityChannelComputer**

Channel derivation logic:
- **Hedonic (Litman I/D):** From `PersonalityWeights` — openness-weighted
  domains → I-type, neuroticism-weighted domains → D-type. Threshold blend.
- **Comprehension (Silvia):** Mean confidence of top gaps. High confidence
  gaps = high comprehension (interest). Low confidence gaps = low
  comprehension (confusion). Range [0,1].
- **Focus (Berlyne):** Gap distribution entropy. One dominant gap → SPECIFIC.
  Many similar-intensity gaps → DIVERSIVE. Threshold at normalized entropy 0.6.
- **Gap proximity (Loewenstein):** Non-linear function of mean gap confidence.
  Peak at ~0.5 confidence, low at 0 and 1. Uses the same binary entropy as
  InformationGapDetector.

- [ ] **Step 5: Run tests and iterate until green**

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(#596): add CuriosityChannel and CuriosityChannelComputer

Four-channel curiosity model: Litman I/D hedonic, Silvia comprehension,
Berlyne specific/diversive focus, Loewenstein gap proximity. Channels
derived from personality weights and gap score distribution.

Refs #596"
```

---

### Task 4: CuriosityDescriptionGenerationPhase — consolidation-time LLM prose

Consolidation phase that generates four-channel curiosity prose, following
the pattern of DriveDescriptionGenerationPhase and
SearchBudgetDescriptionGenerationPhase.

**Files:**
- Create: `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/CuriosityDescriptionGenerationPhase.java`
- Test: `mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/CuriosityDescriptionGenerationPhaseTest.java`

**Interfaces:**
- Consumes: `InformationGapDetector.detectGaps()`, `CuriosityChannelComputer.compute()`, `CognitiveDefaultsRegistry`, `MindMapStore`, `SessionPipeline`
- Produces: COGNITIVE subgraph nodes with trait `CuriosityDescriptionGenerated` containing `description` property

- [ ] **Step 1: Write failing test**

Test that the phase creates a node with the `CuriosityDescriptionGenerated`
trait in the COGNITIVE subgraph when gaps are present.

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Implement CuriosityDescriptionGenerationPhase**

`@Priority(29)` — after SearchBudgetDescriptionGenerationPhase (28),
before CommunitySummaryPhase (30). LLM system prompt instructs four-channel
rendering with personality-specific emphasis. Fallback generates programmatic
prose from channel values when no SessionPipeline available.

System prompt template:
```
You describe how curiosity feels from the inside for this specific character.
Four channels shape the experience:
- Hedonic: Is this pleasurable exploration (I-type) or an aversive need-to-know (D-type)?
- Comprehension: Are the pieces connecting (interest) or is this overwhelming (confusion)?
- Focus: Is attention locked on one question (specific) or scanning broadly (diversive)?
- Gap proximity: How close to knowing — tip of the tongue, or vast unknown?

Write in second person. 3-5 sentences. Do not name the channels.
Respond with JSON only: {"description": "..."}
```

- [ ] **Step 4: Run tests and iterate**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(#596): add CuriosityDescriptionGenerationPhase @Priority(29)

Consolidation-time LLM prose generation for four-channel curiosity.
Crystallises to COGNITIVE subgraph with CuriosityDescriptionGenerated trait.
Fallback programmatic rendering when no SessionPipeline available.

Refs #596"
```

---

## Batch 3: Proportional Rendering and Prompt Integration

### Task 5: CuriosityPromptSection with proportional rendering

Prompt section that reads crystallised curiosity prose or falls back to
programmatic four-channel rendering with proportional context (baseline
deviation, drive rank, momentum).

**Files:**
- Create: `cognition/src/main/java/io/casehub/neocortex/cognition/prompt/CuriosityPromptSection.java`
- Modify: `cognition-api/src/main/java/io/casehub/neocortex/cognition/prompt/BlockTag.java` — add `CURIOSITY` tag
- Test: `cognition/src/test/java/io/casehub/neocortex/cognition/prompt/CuriosityPromptSectionTest.java`

**Interfaces:**
- Consumes: `CuriosityDrive.lastGaps()`, `CuriosityChannelComputer`, `DriveOrchestrator.currentDrives()`, `CognitiveDefaultsRegistry` (baseline), `AffectTrajectoryAnalyzer` (momentum), `MindMapStore` (crystallised prose)
- Produces: `@Nullable String` prompt text with three proportionality dimensions

- [ ] **Step 1: Add BlockTag.CURIOSITY**

Check current highest tag ordinal in BlockTag and add CURIOSITY at an
appropriate position.

- [ ] **Step 2: Write failing tests for CuriosityPromptSection**

Test cases:
- Crystallised prose exists → returns it
- No prose, gaps present → programmatic rendering with channels
- Proportional: curiosity above baseline → "stronger than usual" framing
- Proportional: curiosity is dominant drive → rank context rendered
- No gaps → returns null

- [ ] **Step 3: Run to verify failure**

- [ ] **Step 4: Implement CuriosityPromptSection**

Proportional rendering logic:
1. **Baseline deviation:** Compare current curiosity intensity to
   `CognitiveDefaults`-derived baseline. Above → "more than usual",
   below → "quieter than usual".
2. **Drive rank:** From `DriveProfile` — is curiosity dominant, secondary,
   or suppressed? "Curiosity is the dominant pull" vs "edging behind
   competence".
3. **Momentum:** From `AffectTrajectoryAnalyzer` or drive trajectory — is
   curiosity rising, falling, stable?

Rendering follows the Barrett approach: woven prose, not separate sections
per channel.

- [ ] **Step 5: Run tests and iterate**

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(#596): add CuriosityPromptSection with proportional rendering

Reads crystallised curiosity prose or falls back to four-channel
programmatic rendering. Three proportionality dimensions: baseline
deviation, drive rank, and momentum trajectory.

Refs #596"
```

---

### Task 6: Wire CuriosityPromptSection into CognitionCore and update CognitiveDefaults derivation

Connect the new prompt section to the cognitive pipeline and add
curiosity-specific derivation to CognitiveDerivationEngine.

**Files:**
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCore.java` — add CuriosityPromptSection to renderers
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/CognitionDefaultBeans.java` — produce InformationGapDetector, CuriosityChannelComputer, CuriosityPromptSection beans
- Modify: `cognitive-index/src/main/java/io/casehub/neocortex/cognitive/index/CognitiveDerivationEngine.java` — add `deriveCuriosityChannelWeights` for personality → I/D blend
- Test: existing test suites + new test for derivation

**Interfaces:**
- Consumes: all components from Tasks 1-5
- Produces: fully wired curiosity pipeline in cognition tick lifecycle

- [ ] **Step 1: Write test for I/D blend derivation**

High openness → I-type weight > D-type. High neuroticism → D-type > I-type.

- [ ] **Step 2: Run to verify failure**

- [ ] **Step 3: Add deriveCuriosityChannelWeights to CognitiveDerivationEngine**

Static method: personality facets → I/D blend ratio (0.0 = pure I-type,
1.0 = pure D-type). Store as field on CognitiveDefaults.

- [ ] **Step 4: Wire beans in CognitionDefaultBeans**

Add `@Produces` for `InformationGapDetector`, `CuriosityChannelComputer`,
`CuriosityPromptSection`.

- [ ] **Step 5: Add CuriosityPromptSection to CognitionCore renderer list**

Insert at appropriate position — after drive section, before search budget.

- [ ] **Step 6: Run full build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition,cognition-api,cognitive-index,mindmap-intelligence`

- [ ] **Step 7: Commit**

```bash
git commit -m "feat(#596): wire curiosity pipeline into CognitionCore

Connect InformationGapDetector, CuriosityChannelComputer,
CuriosityPromptSection, and CuriosityDescriptionGenerationPhase
into the cognitive pipeline. Add personality-derived I/D blend
derivation to CognitiveDerivationEngine.

Refs #596"
```

---

## Batch 4: Quality Verification

### Task 7: Integration test — personality differentiation and gap detection quality

End-to-end verification that different personalities produce different
curiosity experiences and that the information-gap model correctly
identifies the partial-knowledge sweet spot.

**Files:**
- Create: `cognition/src/test/java/io/casehub/neocortex/cognition/drive/CuriosityQualityTest.java`

**Interfaces:**
- Consumes: all components from Tasks 1-6
- Produces: quality verification evidence

- [ ] **Step 1: Write quality verification tests**

Test cases:
1. **Personality differentiation:** High-openness agent produces I-type
   hedonic; high-neuroticism agent produces D-type hedonic. Same gap
   data, different felt experience.
2. **Loewenstein non-linearity:** Confidence 0.4 with 3 edges produces
   higher gap intensity than confidence 0.05 with 0 edges AND confidence
   0.95 with 5 edges. The partial-knowledge sweet spot is verified.
3. **Berlyne focus:** Single high-intensity gap → SPECIFIC. Five
   similar-intensity gaps → DIVERSIVE.
4. **Proportional rendering:** Same gap data with curiosity baseline 0.3
   vs 0.7 produces different baseline-deviation framing.
5. **Search budget integration:** Gap-based curiosity flows through
   DriveOrchestrator into SearchBudgetTickParticipant — verify the
   budget reflects curiosity-driven exploration.

- [ ] **Step 2: Run tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=CuriosityQualityTest`

- [ ] **Step 3: Iterate until all quality gates pass**

- [ ] **Step 4: Commit**

```bash
git commit -m "test(#596): add quality verification — personality differentiation + Loewenstein non-linearity

Verify: I/D hedonic differentiates by personality, partial-knowledge
sweet spot peaks at moderate confidence, Berlyne focus classification,
proportional rendering baseline deviation, search budget integration.

Refs #596"
```

---

### Task 8: Update CLAUDE.md and documentation

Update the project documentation to reflect the new curiosity model.

**Files:**
- Modify: `CLAUDE.md` — update CuriosityDrive description, add CuriosityChannel, CuriosityChannelComputer, InformationGapDetector, InformationGapScore, CuriosityDescriptionGenerationPhase, CuriosityPromptSection
- Modify: `cognition/src/main/resources/cognitive-pipeline-defaults.properties` — add curiosity channel parameters if needed

**Interfaces:**
- N/A — documentation only

- [ ] **Step 1: Update CLAUDE.md module descriptions**

Add new types to the relevant module sections. Update CuriosityDrive
description from "retention-based" to "information-gap-based".

- [ ] **Step 2: Commit**

```bash
git commit -m "docs(#596): update CLAUDE.md with information-gap curiosity model

Refs #596"
```

---

## References

- [#596](https://github.com/casehubio/neocortex/issues/596) — focal issue
- [#841](https://github.com/casehubio/neocortex/issues/841) — parent epic
- [#825](https://github.com/casehubio/neocortex/issues/825) — search budget foundation (prerequisite, complete)
- [#826](https://github.com/casehubio/neocortex/issues/826) — recurring thought continuum (downstream)
- `cognition/src/main/java/io/casehub/neocortex/cognition/drive/CuriosityDrive.java` — current retention-based implementation
- `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/CuriositySignalGenerator.java` — existing Berlyne signals
- `cognition/src/main/java/io/casehub/neocortex/cognition/search/SearchBudgetTickParticipant.java` — search budget consumer
- `cognition/src/main/java/io/casehub/neocortex/cognition/search/SearchBudgetPromptSection.java` — crystallised prose pattern
- `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/DriveDescriptionGenerationPhase.java` — Barrett 4-channel template
- `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/SearchBudgetDescriptionGenerationPhase.java` — consolidation generation template
- `cognitive-index/src/main/java/io/casehub/neocortex/cognitive/index/CognitiveDerivationEngine.java:581` — search budget derivation pattern
- `cognition-api/src/main/java/io/casehub/neocortex/cognition/drive/DriveProfile.java` — drive profile record
- Loewenstein, G. (1994). The psychology of curiosity. Psychological Bulletin, 116(1).
- Golman, R. & Loewenstein, G. (2016). Information-gap theory. Carnegie Mellon Working Paper.
- Litman, J.A. (2005, 2008). Interest/Deprivation curiosity model.
- Silvia, P. (2006, 2008). Appraisal theory of interest.
- Berlyne, D.E. (1960). Conflict, Arousal, and Curiosity.
