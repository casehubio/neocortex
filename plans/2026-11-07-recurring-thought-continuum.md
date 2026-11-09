# Recurring Thought Continuum Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #826 — Recurring thought continuum: curiosity → fixation → obsession → rumination as unified CAPS dynamics
**Issue group:** #841

**Goal:** Implement a continuum classifier that derives recurring thought position (HEALTHY/FIXATION/OBSESSION/RUMINATION) from existing data, wires the search budget's pathological threshold, and modulates prompt rendering.

**Architecture:** A ConsolidationPhase (@Priority(25)) scans experience memories and MindMap nodes to score seven symptoms per topic, producing a composite ContinuumPosition. The classification flows to prompt sections via ConsolidationArtifact → CognitionCore → constructor injection. A separate tick-level wire-up in SearchBudgetTickParticipant collapses convergence when anxiousness + habituation failure exceeds the pathological threshold.

**Tech Stack:** Java 21, Quarkus 3.32.2, CDI

## Global Constraints

- No new modules — all components fit existing module boundaries
- No new state stored — continuum position is a pure function of existing data
- No LLM-based classification — threshold-based, mechanical
- Pre-release project — no backward compatibility required
- All commits reference `Refs #826`

---

## Batch 1: Foundation Types and Config

### Task 1: ContinuumPosition enum, RecurringThoughtAssessment record, SignalCategory extension

**Files:**
- Create: `mindmap-api/src/main/java/io/casehub/neocortex/mindmap/ContinuumPosition.java`
- Create: `mindmap-api/src/main/java/io/casehub/neocortex/mindmap/RecurringThoughtAssessment.java`
- Modify: `mindmap-api/src/main/java/io/casehub/neocortex/mindmap/SignalCategory.java` — add THOUGHT_RECURRENCE
- Test: `mindmap-testing/src/test/java/io/casehub/neocortex/mindmap/testing/ContinuumPositionTest.java`

**Interfaces:**
- Consumes: nothing (foundation types)
- Produces: `ContinuumPosition` enum (HEALTHY, FIXATION, OBSESSION, RUMINATION), `RecurringThoughtAssessment` record (topicNodeId, 7 symptom scores, compositeScore, position), `SignalCategory.THOUGHT_RECURRENCE`

- [ ] **Step 1: Write the failing test**

```java
package io.casehub.neocortex.mindmap.testing;

import io.casehub.neocortex.mindmap.ContinuumPosition;
import io.casehub.neocortex.mindmap.RecurringThoughtAssessment;
import io.casehub.neocortex.mindmap.SignalCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContinuumPositionTest {

    @Test
    void enumHasFourPositions() {
        assertEquals(4, ContinuumPosition.values().length);
        assertNotNull(ContinuumPosition.HEALTHY);
        assertNotNull(ContinuumPosition.FIXATION);
        assertNotNull(ContinuumPosition.OBSESSION);
        assertNotNull(ContinuumPosition.RUMINATION);
    }

    @Test
    void assessmentRecordRoundTrip() {
        var assessment = new RecurringThoughtAssessment(
                "node-1", 0.5, 0.3, 0.2, 0.1, 0.4, 0.6, 0.0, 0.3, ContinuumPosition.FIXATION);
        assertEquals("node-1", assessment.topicNodeId());
        assertEquals(0.5, assessment.thoughtRecurrence());
        assertEquals(ContinuumPosition.FIXATION, assessment.position());
    }

    @Test
    void signalCategoryIncludesThoughtRecurrence() {
        assertNotNull(SignalCategory.THOUGHT_RECURRENCE);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-testing -Dtest=ContinuumPositionTest -DfailIfNoTests=false`
Expected: FAIL — classes not found

- [ ] **Step 3: Implement ContinuumPosition enum**

Create `mindmap-api/src/main/java/io/casehub/neocortex/mindmap/ContinuumPosition.java`:

```java
package io.casehub.neocortex.mindmap;

public enum ContinuumPosition {
    HEALTHY,
    FIXATION,
    OBSESSION,
    RUMINATION
}
```

- [ ] **Step 4: Implement RecurringThoughtAssessment record**

Create `mindmap-api/src/main/java/io/casehub/neocortex/mindmap/RecurringThoughtAssessment.java`:

```java
package io.casehub.neocortex.mindmap;

public record RecurringThoughtAssessment(
        String topicNodeId,
        double thoughtRecurrence,
        double habituationFailure,
        double gapClosureResistance,
        double attentionalCapture,
        double arousalTrajectory,
        double valence,
        double actionTendency,
        double compositeScore,
        ContinuumPosition position) {
}
```

- [ ] **Step 5: Add THOUGHT_RECURRENCE to SignalCategory**

In `mindmap-api/src/main/java/io/casehub/neocortex/mindmap/SignalCategory.java`, add `THOUGHT_RECURRENCE` after `BELIEF_REVISED`. Use `ide_replace_member` to update the enum.

- [ ] **Step 6: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-testing -Dtest=ContinuumPositionTest`
Expected: PASS

- [ ] **Step 7: Build full project to check compilation**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn compile -pl mindmap-api,mindmap-testing`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add mindmap-api/src/main/java/io/casehub/neocortex/mindmap/ContinuumPosition.java \
        mindmap-api/src/main/java/io/casehub/neocortex/mindmap/RecurringThoughtAssessment.java \
        mindmap-api/src/main/java/io/casehub/neocortex/mindmap/SignalCategory.java \
        mindmap-testing/src/test/java/io/casehub/neocortex/mindmap/testing/ContinuumPositionTest.java
git commit -m "feat(#826): add ContinuumPosition enum, RecurringThoughtAssessment record, THOUGHT_RECURRENCE signal

Refs #826"
```

### Task 2: RecurringThoughtConfig + CognitiveDefaults field 19 + CDE derivation + DefaultBeans

**Files:**
- Create: `cognitive-api/src/main/java/io/casehub/neocortex/cognitive/RecurringThoughtConfig.java`
- Modify: `cognitive-index/src/main/java/io/casehub/neocortex/cognitive/index/CognitiveDefaults.java` — add field 19
- Modify: `cognitive-index/src/main/java/io/casehub/neocortex/cognitive/index/CognitiveDerivationEngine.java` — add 14th derivation pathway
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/CognitionDefaultBeans.java` — add producer
- Test: `cognitive-index/src/test/java/io/casehub/neocortex/cognitive/index/RecurringThoughtDerivationTest.java`

**Interfaces:**
- Consumes: `ContinuumPosition` from Task 1
- Produces: `RecurringThoughtConfig` record (symptomWeights, fixationThreshold, obsessionThreshold, ruminationThreshold, recurrenceWindow, minObservations), `CognitiveDefaults.recurringThoughtConfig()`, `CognitiveDerivationEngine.deriveRecurringThoughtConfig()`

- [ ] **Step 1: Write the failing test**

```java
package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.RecurringThoughtConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecurringThoughtDerivationTest {

    @Test
    void defaultConfigHasEqualWeightsAndStandardThresholds() {
        var config = RecurringThoughtConfig.defaults();
        assertTrue(config.symptomWeights().isEmpty());
        assertEquals(0.3, config.fixationThreshold());
        assertEquals(0.6, config.obsessionThreshold());
        assertEquals(0.8, config.ruminationThreshold());
        assertEquals(7, config.recurrenceWindow().toDays());
        assertEquals(3, config.minObservations());
    }

    @Test
    void highNeuroticismLowersFixationThreshold() {
        // High neuroticism disposition lowers fixation threshold
        var derived = CognitiveDerivationEngine.deriveRecurringThoughtConfig(
                highNeuroticismWeights());
        assertTrue(derived.fixationThreshold() < 0.3,
                "High neuroticism should lower fixation threshold below default 0.3");
    }

    @Test
    void highConscientiousnessRaisesObsessionThreshold() {
        var derived = CognitiveDerivationEngine.deriveRecurringThoughtConfig(
                highConscientiousnessWeights());
        assertTrue(derived.obsessionThreshold() > 0.6,
                "High conscientiousness should raise obsession threshold above default 0.6");
    }

    // Helper methods provide WeightedTerms with personality-representative profiles
    // (implementations depend on the existing WeightedTerms/DispositionAxes API — 
    //  read CognitiveDerivationEngine to see how other derive* tests construct profiles)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-index -Dtest=RecurringThoughtDerivationTest -DfailIfNoTests=false`
Expected: FAIL — RecurringThoughtConfig not found

- [ ] **Step 3: Implement RecurringThoughtConfig record**

Create `cognitive-api/src/main/java/io/casehub/neocortex/cognitive/RecurringThoughtConfig.java`:

```java
package io.casehub.neocortex.cognitive;

import java.time.Duration;
import java.util.Map;

public record RecurringThoughtConfig(
        Map<String, Double> symptomWeights,
        double fixationThreshold,
        double obsessionThreshold,
        double ruminationThreshold,
        Duration recurrenceWindow,
        int minObservations) {

    public static RecurringThoughtConfig defaults() {
        return new RecurringThoughtConfig(
                Map.of(),
                0.3, 0.6, 0.8,
                Duration.ofDays(7),
                3);
    }
}
```

- [ ] **Step 4: Add recurringThoughtConfig to CognitiveDefaults**

Use `ide_replace_member` on `CognitiveDefaults` record to add `RecurringThoughtConfig recurringThoughtConfig` as field 19 after `searchBudgetConfig`. Update the `empty()` factory method to include the 19th null positional argument. Update `CognitiveDefaultsRegistry` YAML parsing to read `recurringThoughtConfig` from profiles. Update `deriveAndMerge()` to overlay the field.

- [ ] **Step 5: Add deriveRecurringThoughtConfig to CognitiveDerivationEngine**

Use `ide_insert_member` to add the 14th derivation method. The method derives config adjustments from the disposition profile:
- High neuroticism (emotional instability terms) → lower fixationThreshold by 0.05
- High conscientiousness → raise obsessionThreshold by 0.05, lower attentionalCapture weight
- High openness → broaden recurrenceWindow by 2 days
- High autonomy + low agreeableness → lower ruminationThreshold by 0.05

Follow the pattern of existing derive methods (e.g., `deriveHabituationConfig`). Call from `derive()`.

- [ ] **Step 6: Add @DefaultBean producer in CognitionDefaultBeans**

Use `ide_insert_member` to add:
```java
@Produces @DefaultBean @Singleton
RecurringThoughtConfig recurringThoughtConfig() {
    return RecurringThoughtConfig.defaults();
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-index -Dtest=RecurringThoughtDerivationTest`
Expected: PASS

- [ ] **Step 8: Build affected modules**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn compile -pl cognitive-api,cognitive-index,cognition`
Expected: BUILD SUCCESS (may need to fix CognitiveDefaults constructor usages across modules)

- [ ] **Step 9: Commit**

```bash
git add cognitive-api/src/main/java/io/casehub/neocortex/cognitive/RecurringThoughtConfig.java \
        cognitive-index/src/main/java/io/casehub/neocortex/cognitive/index/CognitiveDefaults.java \
        cognitive-index/src/main/java/io/casehub/neocortex/cognitive/index/CognitiveDerivationEngine.java \
        cognition/src/main/java/io/casehub/neocortex/cognition/CognitionDefaultBeans.java \
        cognitive-index/src/test/java/io/casehub/neocortex/cognitive/index/RecurringThoughtDerivationTest.java
git commit -m "feat(#826): add RecurringThoughtConfig, CognitiveDefaults field 19, CDE derivation

14th derivation pathway: disposition → recurring thought susceptibility.
High neuroticism lowers fixation threshold, high conscientiousness raises
obsession threshold.

Refs #826"
```

---

## Batch 2: Scorer and ConsolidationPhase

### Task 3: RecurringThoughtScorer

**Files:**
- Create: `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/RecurringThoughtScorer.java`
- Test: `mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/RecurringThoughtScorerTest.java`

**Interfaces:**
- Consumes: `ContinuumPosition`, `RecurringThoughtAssessment` (Task 1), `RecurringThoughtConfig` (Task 2), `MindMapStore`, `CaseMemoryStore`, `AttentionBriefing`, `HabituationState`
- Produces: `RecurringThoughtScorer.score(topicNodeId, tenantId, config, briefing, habituation) → RecurringThoughtAssessment`

- [ ] **Step 1: Write the failing test**

```java
package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.RecurringThoughtConfig;
import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecurringThoughtScorerTest {

    private InMemoryMindMapStore mindMapStore;
    private InMemoryMemoryStore memoryStore;
    private RecurringThoughtScorer scorer;

    @BeforeEach
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        memoryStore = new InMemoryMemoryStore();
        scorer = new RecurringThoughtScorer(mindMapStore, memoryStore);
    }

    @Test
    void noDataReturnsHealthy() {
        var assessment = scorer.score("node-1", "tenant-1",
                RecurringThoughtConfig.defaults(), null, null);
        assertEquals(ContinuumPosition.HEALTHY, assessment.position());
        assertEquals(0.0, assessment.compositeScore(), 0.01);
    }

    @Test
    void highRecurrenceAndNegativeValenceProducesFixation() {
        // Set up: create a node with negative PAD pleasure, add multiple experience memories
        // referencing this node within the recurrence window
        var node = createNodeWithPad("topic-node", -0.5, 0.7, 0.3);
        addExperienceMemories("topic-node", 6); // above minObservations=3

        var assessment = scorer.score("topic-node", "tenant-1",
                RecurringThoughtConfig.defaults(), null, null);
        assertTrue(assessment.compositeScore() >= 0.3,
                "High recurrence + negative valence should score >= FIXATION threshold");
        assertNotEquals(ContinuumPosition.HEALTHY, assessment.position());
    }

    @Test
    void belowMinObservationsAlwaysHealthy() {
        var node = createNodeWithPad("topic-node", -0.9, 0.9, 0.1);
        addExperienceMemories("topic-node", 2); // below minObservations=3

        var assessment = scorer.score("topic-node", "tenant-1",
                RecurringThoughtConfig.defaults(), null, null);
        assertEquals(ContinuumPosition.HEALTHY, assessment.position());
    }

    @Test
    void weightedScoringRespectsConfig() {
        // Custom config with all weight on valence only
        var config = new RecurringThoughtConfig(
                Map.of("valence", 1.0, "thoughtRecurrence", 0.0, "habituationFailure", 0.0,
                       "gapClosureResistance", 0.0, "attentionalCapture", 0.0,
                       "arousalTrajectory", 0.0, "actionTendency", 0.0),
                0.3, 0.6, 0.8, Duration.ofDays(7), 1);
        var node = createNodeWithPad("topic-node", -0.8, 0.0, 0.0);
        addExperienceMemories("topic-node", 3);

        var assessment = scorer.score("topic-node", "tenant-1", config, null, null);
        assertEquals(assessment.valence(), assessment.compositeScore(), 0.01);
    }

    // Helper methods: createNodeWithPad, addExperienceMemories
    // (Use InMemoryMindMapStore.addNode, InMemoryMemoryStore.store with domain="experience")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest=RecurringThoughtScorerTest -DfailIfNoTests=false`
Expected: FAIL — RecurringThoughtScorer not found

- [ ] **Step 3: Implement RecurringThoughtScorer**

Create `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/RecurringThoughtScorer.java`:

Instance class with `MindMapStore` and `CaseMemoryStore` constructor-injected. The `score()` method computes each symptom:

1. **thoughtRecurrence** — `CaseMemoryStore.scan()` with domain="experience", count memories where text or SUBJECT attribute references the topic node name within `recurrenceWindow`. Normalize: `min(1.0, count / 10.0)`.
2. **habituationFailure** — if `habituation != null`: check `observationCounts()` and `noveltyScores()` maps for any hash exceeding `repetitionTolerance` while novelty > `noveltyThreshold`. Cross-session: split experience memories in recurrence window into halves by timestamp, compare counts — recent half ≥ older half → 1.0, else 0.0. Combine: `max(inSession, crossSession)`.
3. **gapClosureResistance** — read node confidence from `MindMapStore`. If confidence < 0.5, score = `1.0 - confidence`. Else 0.0.
4. **attentionalCapture** — if `briefing != null`: count signals with `sourceNodeId` matching topic node / total signals. Else 0.0.
5. **arousalTrajectory** — read node PAD arousal. If arousal > 0.5, score = `arousal`. If arousal < -0.3 and pleasure < -0.3, score = 0.9 (rumination signal). Else `max(0, arousal)`.
6. **valence** — read node PAD pleasure. Score = `max(0, -pleasure)` (inverted: negative pleasure → higher score).
7. **actionTendency** — query BEHAVIORAL subgraph for CapsGenerated-trait nodes linked to topic. Map category strings using keyword matching: approach→0.0, mixed→0.5, avoidance→0.75, withdrawal→1.0. Use strongest attractor.

Composite = weighted sum / sum of weights. Map to ContinuumPosition via thresholds.

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest=RecurringThoughtScorerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/RecurringThoughtScorer.java \
        mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/RecurringThoughtScorerTest.java
git commit -m "feat(#826): add RecurringThoughtScorer — seven-symptom composite classifier

Instance class with constructor-injected stores. Derives continuum
position from experience frequency, habituation, confidence, attention
signals, affect trajectory, PAD valence, and CAPS attractors.

Refs #826"
```

### Task 4: ConsolidationArtifact variant + RecurringThoughtPhase + ConsolidationMediator extension

**Files:**
- Modify: `mindmap-api/src/main/java/io/casehub/neocortex/mindmap/ConsolidationArtifact.java` — add `RecurringThoughtDetected` permit
- Create: `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/RecurringThoughtPhase.java`
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/core/ConsolidationMediator.java` — extend `isRelevant()` switch
- Test: `mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/RecurringThoughtPhaseTest.java`

**Interfaces:**
- Consumes: `RecurringThoughtScorer` (Task 3), `RecurringThoughtConfig` (Task 2), `CaseMemoryStore`, `MindMapStore`, `CognitiveDefaultsRegistry`
- Produces: `ConsolidationArtifact.RecurringThoughtDetected` (per non-HEALTHY topic), `AttentionSignal` with `THOUGHT_RECURRENCE` category, cached `List<RecurringThoughtAssessment>` per agentId:tenantId

- [ ] **Step 1: Write the failing test**

```java
package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.RecurringThoughtConfig;
import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecurringThoughtPhaseTest {

    private RecurringThoughtPhase phase;
    private InMemoryMindMapStore mindMapStore;
    private InMemoryMemoryStore memoryStore;

    @BeforeEach
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        memoryStore = new InMemoryMemoryStore();
        // Wire phase with store dependencies and a defaults registry
        // that returns RecurringThoughtConfig.defaults()
    }

    @Test
    void noExperienceMemoriesProducesNoSignals() {
        phase.run("tenant-1", List.of());
        assertTrue(phase.signals().isEmpty());
        assertTrue(phase.artifacts().isEmpty());
    }

    @Test
    void fixationTopicEmitsSignalAndArtifact() {
        // Set up: node with negative PAD, many experience memories referencing it
        createFixationScenario("topic-1", "agent-1", "tenant-1");

        phase.run("tenant-1", List.of());

        var signals = phase.signals();
        assertFalse(signals.isEmpty(), "Should emit THOUGHT_RECURRENCE signal");
        assertEquals(SignalCategory.THOUGHT_RECURRENCE, signals.get(0).category());
        assertEquals(0.4, signals.get(0).significance(), 0.1); // FIXATION = 0.4

        var artifacts = phase.artifacts();
        assertFalse(artifacts.isEmpty(), "Should produce RecurringThoughtDetected artifact");
    }

    @Test
    void belowMinObservationsNoSignals() {
        // Only 2 memories (below default minObservations=3)
        createSparseScenario("topic-1", "agent-1", "tenant-1", 2);

        phase.run("tenant-1", List.of());
        assertTrue(phase.signals().isEmpty());
    }

    @Test
    void cachedAssessmentsAccessible() {
        createFixationScenario("topic-1", "agent-1", "tenant-1");
        phase.run("tenant-1", List.of());

        var cached = phase.getAssessments("agent-1", "tenant-1");
        assertNotNull(cached);
        assertFalse(cached.isEmpty());
    }

    // Helper methods: createFixationScenario, createSparseScenario
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest=RecurringThoughtPhaseTest -DfailIfNoTests=false`
Expected: FAIL — RecurringThoughtPhase not found

- [ ] **Step 3: Add RecurringThoughtDetected to ConsolidationArtifact**

In `mindmap-api/.../ConsolidationArtifact.java`, add to the `permits` clause:

```java
record RecurringThoughtDetected(
        String tenantId, Instant timestamp,
        RecurringThoughtAssessment assessment
) implements ConsolidationArtifact {}
```

- [ ] **Step 4: Implement RecurringThoughtPhase**

Create `mindmap-intelligence/.../consolidation/RecurringThoughtPhase.java`:

```java
@Priority(25)
public class RecurringThoughtPhase implements ConsolidationPhase {
    private final Instance<CaseMemoryStore> memoryStoreInstance;
    private final Instance<MindMapStore> mindMapStoreInstance;
    private final Instance<CognitiveDefaultsRegistry> registryInstance;
    private final ConcurrentHashMap<String, List<RecurringThoughtAssessment>> cache = new ConcurrentHashMap<>();
    private final List<AttentionSignal> pendingSignals = new ArrayList<>();
    private final List<ConsolidationArtifact> pendingArtifacts = new ArrayList<>();

    // Constructor with Instance<T> injection for graceful degradation

    @Override
    public String name() { return "recurring-thought"; }

    @Override
    public void beginTick() {
        pendingSignals.clear();
        pendingArtifacts.clear();
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        // 1. Guard: if no CaseMemoryStore, return
        // 2. Discover agents from COGNITIVE subgraph (agent-id property grouping)
        // 3. For each agent:
        //    a. Get RecurringThoughtConfig from registry (or defaults)
        //    b. Scan experience memories within recurrenceWindow
        //    c. Extract topic node references, resolve to MindMap nodes
        //    d. Score each topic via RecurringThoughtScorer
        //    e. Apply minObservations guard
        //    f. Cache assessments
        //    g. For non-HEALTHY: emit signal + produce artifact
    }

    @Override
    public List<AttentionSignal> signals() {
        return List.copyOf(pendingSignals); // drain semantics
    }

    @Override
    public List<ConsolidationArtifact> artifacts() {
        return List.copyOf(pendingArtifacts);
    }

    public List<RecurringThoughtAssessment> getAssessments(String agentId, String tenantId) {
        return cache.getOrDefault(agentId + ":" + tenantId, List.of());
    }
}
```

Significance scaling: `FIXATION=0.4, OBSESSION=0.7, RUMINATION=1.0`.

- [ ] **Step 5: Extend ConsolidationMediator.isRelevant()**

In `cognition/.../ConsolidationMediator.java`, add a case to the exhaustive switch on `ConsolidationArtifact`:

```java
case ConsolidationArtifact.RecurringThoughtDetected r -> true;
```

- [ ] **Step 6: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest=RecurringThoughtPhaseTest`
Expected: PASS

- [ ] **Step 7: Build affected modules to check compilation**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn compile -pl mindmap-api,mindmap-intelligence,cognition`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add mindmap-api/src/main/java/io/casehub/neocortex/mindmap/ConsolidationArtifact.java \
        mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/RecurringThoughtPhase.java \
        cognition/src/main/java/io/casehub/neocortex/cognition/core/ConsolidationMediator.java \
        mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/RecurringThoughtPhaseTest.java
git commit -m "feat(#826): add RecurringThoughtPhase @Priority(25) and RecurringThoughtDetected artifact

ConsolidationPhase that scores topics per-agent, caches assessments,
emits THOUGHT_RECURRENCE signals, and produces artifacts for prompt
rendering pipeline.

Refs #826"
```

---

## Batch 3: PathologicalThreshold Wire-up + Prompt Rendering

### Task 5: Wire pathologicalThreshold in SearchBudgetTickParticipant

**Files:**
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/search/SearchBudgetTickParticipant.java`
- Test: `cognition/src/test/java/io/casehub/neocortex/cognition/search/SearchBudgetPathologicalThresholdTest.java`

**Interfaces:**
- Consumes: `HabituationState` (from AppraisalTickParticipant), `HabituationConfig` (from CognitiveDefaultsRegistry), `SearchBudgetConfig.pathologicalThreshold()`
- Produces: convergence=0.0 when anxiousness + habituation failure exceeds pathologicalThreshold

- [ ] **Step 1: Write the failing test**

```java
package io.casehub.neocortex.cognition.search;

import io.casehub.neocortex.cognitive.HabituationConfig;
import io.casehub.neocortex.cognition.appraisal.HabituationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearchBudgetPathologicalThresholdTest {

    @Test
    void convergenceCollapsesWhenThresholdExceeded() {
        // anxiousnessScore=0.8, habituationFailure=true (count > repetitionTolerance, novelty > threshold)
        // 0.8 + 1.0 = 1.8 > default threshold 1.4 → convergence must be 0.0
        var habituation = createHabituationWithFailure();
        double convergence = computeConvergenceWithPathological(
                0.5, /* intensity */ null, /* dominant */
                defaultConfig(), null, /* gut */
                habituation, HabituationConfig.defaults(), 0.8 /* anxiousness */);
        assertEquals(0.0, convergence, 0.001);
    }

    @Test
    void convergenceUnchangedWhenBelowThreshold() {
        // anxiousnessScore=0.2, no habituation failure → 0.2 + 0.0 = 0.2 < 1.4
        var habituation = createNormalHabituation();
        double convergence = computeConvergenceWithPathological(
                0.5, null, defaultConfig(), null,
                habituation, HabituationConfig.defaults(), 0.2);
        assertTrue(convergence > 0.0, "Should not collapse when below threshold");
    }

    @Test
    void nullHabituationSkipsCheck() {
        double convergence = computeConvergenceWithPathological(
                0.5, null, defaultConfig(), null,
                null, HabituationConfig.defaults(), 0.9);
        assertTrue(convergence > 0.0, "Should not collapse when no habituation state");
    }

    // Helper methods call the extended computeConvergence via reflection or package access
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=SearchBudgetPathologicalThresholdTest -DfailIfNoTests=false`
Expected: FAIL

- [ ] **Step 3: Extend computeConvergence() signature and add wire-up logic**

In `SearchBudgetTickParticipant.java`:

1. Extend `computeConvergence()` to accept `@Nullable HabituationState habituation`, `HabituationConfig habConfig`, `double anxiousnessScore`.

2. After existing logistic computation, add:
```java
if (habituation != null) {
    boolean anyFailure = habituation.observationCounts().entrySet().stream()
            .anyMatch(e -> e.getValue() > habConfig.repetitionTolerance()
                        && habituation.noveltyScores().getOrDefault(e.getKey(), 1.0)
                           > habConfig.noveltyThreshold());
    if (anxiousnessScore + (anyFailure ? 1.0 : 0.0) > config.pathologicalThreshold()) {
        convergence = 0.0;
    }
}
```

3. Update the `tick()` call site to pass `HabituationState` from `appraisalParticipant` and `HabituationConfig` from `defaultsRegistry`, and arousal score from appraisal result.

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=SearchBudgetPathologicalThresholdTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add cognition/src/main/java/io/casehub/neocortex/cognition/search/SearchBudgetTickParticipant.java \
        cognition/src/test/java/io/casehub/neocortex/cognition/search/SearchBudgetPathologicalThresholdTest.java
git commit -m "feat(#826): wire pathologicalThreshold in SearchBudgetTickParticipant

Global convergence collapse when anxiousnessScore + habituationFailure
exceeds SearchBudgetConfig.pathologicalThreshold (default 1.4). Agent-wide
mechanism — per-topic classification is consolidation-level.

Refs #826"
```

### Task 6: CognitionCore artifact drain + prompt section modulation

**Files:**
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCore.java` — drain RecurringThoughtDetected, pass continuum map to prompt sections
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/prompt/CuriosityPromptSection.java` — accept continuum map, modulate rendering
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/search/SearchBudgetPromptSection.java` — accept continuum map, modulate rendering
- Modify: `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/CuriosityDescriptionGenerationPhase.java` — include continuum position in LLM prompt
- Test: `cognition/src/test/java/io/casehub/neocortex/cognition/prompt/ContinuumRenderingTest.java`

**Interfaces:**
- Consumes: `ConsolidationArtifact.RecurringThoughtDetected` (Task 4), `ContinuumPosition` (Task 1)
- Produces: Modulated prompt text reflecting continuum position

- [ ] **Step 1: Write the failing test**

```java
package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.mindmap.ContinuumPosition;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContinuumRenderingTest {

    @Test
    void curiositySectionHealthyRendersNormally() {
        var section = createCuriositySection(Map.of());
        var output = section.render(testContext());
        // Should render normal four-channel output (no continuum modulation)
        assertNotNull(output);
        assertFalse(output.contains("unbidden"));
    }

    @Test
    void curiositySectionFixationNarrowsToDominantChannel() {
        var section = createCuriositySection(
                Map.of("topic-1", ContinuumPosition.FIXATION));
        var output = section.render(testContext());
        assertNotNull(output);
        // FIXATION should add urgency framing
    }

    @Test
    void curiositySectionObsessionSuppressesChannels() {
        var section = createCuriositySection(
                Map.of("topic-1", ContinuumPosition.OBSESSION));
        var output = section.render(testContext());
        assertNotNull(output);
        assertTrue(output.contains("unbidden") || output.contains("intrusive"),
                "Obsession should render intrusive quality");
    }

    @Test
    void curiositySectionRuminationShowsWithdrawal() {
        var section = createCuriositySection(
                Map.of("topic-1", ContinuumPosition.RUMINATION));
        var output = section.render(testContext());
        assertNotNull(output);
        // Should render withdrawal framing
    }

    @Test
    void searchBudgetFixationShiftsToPressured() {
        var section = createSearchBudgetSection(
                Map.of("topic-1", ContinuumPosition.FIXATION));
        var output = section.render(testContext());
        assertNotNull(output);
    }

    @Test
    void nullContinuumMapRendersNormally() {
        var section = createCuriositySection(null);
        var output = section.render(testContext());
        // Should render without error — backward compatible
    }

    // Helper: createCuriositySection, createSearchBudgetSection with continuum map,
    // testContext returns CognitionRenderContext
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=ContinuumRenderingTest -DfailIfNoTests=false`
Expected: FAIL

- [ ] **Step 3: Add continuum map to CuriosityPromptSection**

Add `@Nullable Map<String, ContinuumPosition> continuum` as a constructor parameter. In `render()`, find the worst (highest) ContinuumPosition across all topics in the map. Switch on position:

- `HEALTHY` or null: existing rendering unchanged
- `FIXATION`: narrow to dominant channel only, add urgency framing, emphasise D-type
- `OBSESSION`: suppress channels, render intrusive quality language
- `RUMINATION`: suppress channels, withdrawal framing, flat affect

- [ ] **Step 4: Add continuum map to SearchBudgetPromptSection**

Add `@Nullable Map<String, ContinuumPosition> continuum` as a constructor parameter. In `render()`, switch on worst position:

- `HEALTHY` or null: existing rendering
- `FIXATION`: change convergence text to "you keep circling back"
- `OBSESSION`: add compulsive quality to depth rendering
- `RUMINATION`: render all parameters as collapsed

- [ ] **Step 5: Modulate CuriosityDescriptionGenerationPhase**

In `CuriosityDescriptionGenerationPhase`, when generating crystallised LLM prose, check the `RecurringThoughtPhase` cache for the topic. If non-HEALTHY, include the `ContinuumPosition` name in the generation prompt so the LLM prose reflects the correct tone. The phase already has access to MindMap nodes — add `Instance<RecurringThoughtPhase>` for cache access (graceful degradation when not on classpath).

- [ ] **Step 6: Wire CognitionCore to drain artifacts and pass to sections**

In `CognitionCore`:
1. After draining `ConsolidationArtifact`s at line ~245, filter for `RecurringThoughtDetected` instances
2. Build `Map<String, ContinuumPosition>` from the assessments
3. Store as a volatile field (same pattern as existing `List<ConsolidationArtifact>`)
4. Pass to `CuriosityPromptSection` and `SearchBudgetPromptSection` constructors in `promptSections()`

- [ ] **Step 7: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=ContinuumRenderingTest`
Expected: PASS

- [ ] **Step 8: Build full project**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCore.java \
        cognition/src/main/java/io/casehub/neocortex/cognition/prompt/CuriosityPromptSection.java \
        cognition/src/main/java/io/casehub/neocortex/cognition/search/SearchBudgetPromptSection.java \
        cognition/src/test/java/io/casehub/neocortex/cognition/prompt/ContinuumRenderingTest.java
git commit -m "feat(#826): modulate CuriosityPromptSection and SearchBudgetPromptSection by ContinuumPosition

CognitionCore drains RecurringThoughtDetected artifacts, builds continuum
map, passes to prompt sections. FIXATION narrows channels, OBSESSION
suppresses, RUMINATION renders withdrawal.

Refs #826"
```

---

## Batch 4: Quality Verification + Docs

### Task 7: Integration tests — personality differentiation + full pipeline

**Files:**
- Create: `cognition/src/test/java/io/casehub/neocortex/cognition/RecurringThoughtIntegrationTest.java`

**Interfaces:**
- Consumes: all components from Tasks 1-6
- Produces: quality verification that personality-different agents classify differently, and that the full pipeline (consolidation → signal → artifact → rendering) works end-to-end

- [ ] **Step 1: Write personality differentiation test**

```java
@Test
void highNeuroticismAgentFixatesEasierThanResilientAgent() {
    // Two agents, same stimuli, different disposition profiles
    // High-neuroticism agent should reach FIXATION with fewer observations
    // than the high-conscientiousness (resilient) agent
    var neuroticism = deriveConfig(highNeuroticismProfile());
    var conscientiousness = deriveConfig(highConscientiousnessProfile());

    assertTrue(neuroticism.fixationThreshold() < conscientiousness.fixationThreshold());

    // Score same topic with identical data, different configs
    var anxiousResult = scorer.score("topic-1", "tenant-1", neuroticism, null, null);
    var resilientResult = scorer.score("topic-1", "tenant-1", conscientiousness, null, null);

    // With borderline data, anxious agent should be fixated, resilient should be healthy
    assertTrue(anxiousResult.position().ordinal() >= resilientResult.position().ordinal());
}
```

- [ ] **Step 2: Write full pipeline test**

```java
@Test
void consolidationToRenderingPipeline() {
    // Set up: experience memories + negative-affect node for a topic
    // Run RecurringThoughtPhase
    // Verify: signal emitted, artifact produced, cached assessment accessible
    // Build continuum map from artifacts
    // Pass to CuriosityPromptSection
    // Verify: rendering reflects continuum position
}
```

- [ ] **Step 3: Write pathological threshold integration test**

```java
@Test
void pathologicalThresholdCollapsesConvergenceInFullTick() {
    // Set up SearchBudgetTickParticipant with real AppraisalTickParticipant
    // Simulate high anxiousness + habituation failure
    // Verify convergence = 0.0 in the resulting SearchBudget
}
```

- [ ] **Step 4: Run all tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=RecurringThoughtIntegrationTest`
Expected: PASS

- [ ] **Step 5: Run full test suite**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test`
Expected: BUILD SUCCESS (no regressions)

- [ ] **Step 6: Commit**

```bash
git add cognition/src/test/java/io/casehub/neocortex/cognition/RecurringThoughtIntegrationTest.java
git commit -m "test(#826): add quality verification — personality differentiation + full pipeline

Verifies high-neuroticism agents fixate easier, full consolidation-to-
rendering pipeline, and pathological threshold convergence collapse.

Refs #826"
```

### Task 8: Update CLAUDE.md and contributor guide

**Files:**
- Modify: `CLAUDE.md` — update cognition, mindmap-intelligence, and cognitive-api module descriptions
- Modify: `docs/guides/contributor-guide.md` — add recurring thought continuum section if consolidation phases are documented

- [ ] **Step 1: Update CLAUDE.md**

Add to cognition module description:
- `RecurringThoughtPhase` (@Priority(25)) in mindmap-intelligence consolidation section
- `RecurringThoughtConfig` in cognitive-api
- `ContinuumPosition`, `RecurringThoughtAssessment`, `SignalCategory.THOUGHT_RECURRENCE` in mindmap-api
- PathologicalThreshold wire-up in cognition SearchBudgetTickParticipant
- Prompt modulation in CuriosityPromptSection and SearchBudgetPromptSection

- [ ] **Step 2: Update contributor guide if applicable**

Add recurring thought continuum to any consolidation pipeline documentation.

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md docs/guides/contributor-guide.md
git commit -m "docs(#826): update CLAUDE.md with recurring thought continuum model

Refs #826"
```

---

## References

- [2026-11-07-recurring-thought-continuum-design.md] — design spec this plan implements
- [mindmap-api/.../SignalCategory.java] — 11-value enum, THOUGHT_RECURRENCE is 12th
- [mindmap-api/.../ConsolidationArtifact.java] — sealed interface with 5 permits, RecurringThoughtDetected is 6th
- [cognition-api/.../CognitionRenderContext.java] — 4-field record
- [cognition/.../CognitionCore.java] — composition root, drains artifacts at ~L245
- [cognition/.../SearchBudgetTickParticipant.java] — computeConvergence() at L129
- [cognition/.../CuriosityPromptSection.java] — four-channel rendering
- [cognition/.../SearchBudgetPromptSection.java] — convergence < 0.05 path at L67
- [cognitive-index/.../CognitiveDefaults.java] — 18 fields, recurringThoughtConfig is 19th
- [cognitive-index/.../CognitiveDerivationEngine.java] — 13 derive methods, recurring thought is 14th
- [cognition/.../CognitionDefaultBeans.java] — CDI producers
- [cognition/.../ConsolidationMediator.java] — exhaustive switch on artifact types
- [mindmap-intelligence/.../BehavioralSynthesisPhase.java] — agent discovery pattern
- [mindmap-intelligence/.../InformationGapDetector.java] — instance class pattern
- [GitHub #826] — issue body with seven symptoms, literature
- [GitHub #825] — search budget dependency
- [GitHub #596] — information-gap curiosity dependency
