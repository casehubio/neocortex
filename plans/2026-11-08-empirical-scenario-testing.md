# Empirical Cognitive Exploration Testing — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #849 — Empirical cognitive exploration testing: scenario-based pipeline verification
**Issue group:** #849

**Goal:** Build a general-purpose `CognitionTestHarness` with fluent builder, and 5 empirical scenarios (S1–S5) that verify integrated cognitive pipeline behavior.

**Architecture:** Fluent builder wraps `CognitionCore` with sensible defaults (in-memory stores, no-op orchestrators, no LLM). Two execution modes: full pipeline (`tickAndConsolidate` — runs consolidation phases then tick) and injection bypass (`setContinuumPosition` then `tick`). Two personality modes: `DispositionAxes` derivation via `CognitiveDerivationEngine` or direct `CognitiveDefaults`. A package-private bridge class (`ConsolidationMediatorTestAccess`) enables artifact injection from the test package.

**Tech Stack:** Java 21 (on Java 26 JVM), JUnit 5, AssertJ, Mockito, InMemoryMindMapStore, InMemoryMemoryStore

## Global Constraints

- Java 21 language level on Java 26 JVM
- No LLM calls — SessionPipeline is null throughout
- All files in `cognition/src/test/java/`
- Standard AssertJ assertions — no custom assertion classes
- No new test-scope dependencies needed (all already in cognition/pom.xml)
- `AgentDescriptor` passed as null to `CognitionCore.tick()` — per-agent config lives in `CognitiveDefaultsRegistry`

---

## Batch 1: CognitionTestHarness + S1 Personality Differentiation

This batch builds the harness and proves it works with S1. After this batch: harness construction, tick execution, search budget access, and prompt rendering all work. S1 verifies that different dispositions produce different search budgets.

### Task 1: CognitionTestHarness + S1

**Files:**
- Create: `cognition/src/test/java/io/casehub/neocortex/cognition/eval/CognitionTestHarness.java`
- Create: `cognition/src/test/java/io/casehub/neocortex/cognition/core/ConsolidationMediatorTestAccess.java`
- Create: `cognition/src/test/java/io/casehub/neocortex/cognition/eval/CognitiveExplorationScenarioTest.java`

**Interfaces:**
- Consumes: `CognitionCore` (18-param constructor), `DriveOrchestrator` (7-param), `MoodOrchestrator(MoodConfig)`, `CuriosityDrive(InformationGapDetector, CuriosityChannelComputer, double, Supplier<Set<String>>)`, `SearchBudgetTickParticipant` (7-param), `CognitiveDefaultsRegistry.forTesting(CognitiveDefaults...)`, `CognitiveDerivationEngine.derive(DescriptorView)`, `InMemoryMindMapStore()`, `InMemoryMemoryStore(CurrentPrincipal)`, `ConsolidationMediator()`
- Produces: `CognitionTestHarness` — reusable by Tasks 2 and 3

- [ ] **Step 1: Write failing S1 test**

Create `CognitiveExplorationScenarioTest.java` with the S1 test method. This test defines the harness API — the harness will be built to make this test compile and pass.

```java
package io.casehub.neocortex.cognition.eval;

import io.casehub.neocortex.cognitive.index.CognitiveDerivationEngine;
import io.casehub.neocortex.cognitive.index.DescriptorView;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.EdgeInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveExplorationScenarioTest {

    private static final String TENANT = "test-tenant";

    @Test
    void s1_personalityDifferentiation() {
        // Set up shared graph with research topics showing partial knowledge
        var store = new InMemoryMindMapStore();
        store.createSubgraph(TENANT, new SubgraphInput("research"));

        // Add 3 nodes with partial knowledge (confidence 0.4-0.6)
        // Use the MindMapStore API — check NodeInput constructor for exact params
        // Pattern: look at RecurringThoughtIntegrationTest for store setup
        var node1 = store.addNode(TENANT, nodeInput("topic-1", "research", 0.45));
        var node2 = store.addNode(TENANT, nodeInput("topic-2", "research", 0.50));
        var node3 = store.addNode(TENANT, nodeInput("topic-3", "research", 0.55));

        // Add edges so InformationGapDetector finds gaps (needs edge count > 0)
        store.addEdge(TENANT, edgeInput(node1, node2, "relates-to"));
        store.addEdge(TENANT, edgeInput(node2, node3, "relates-to"));
        store.addEdge(TENANT, edgeInput(node1, node3, "relates-to"));

        // Two agents with contrasting dispositions
        // DispositionAxes(socialOrient, ruleFollowing, riskAppetite, autonomy, conflictMode)
        var harness = CognitionTestHarness.builder()
            .withMindMapStore(store)
            .agentFromAxes("anxious", "reserved", "flexible", "cautious", "low", "avoidant")
            .agentFromAxes("steady", "cooperative", "strict", "measured", "high", "analytical")
            .build();

        // Same observation for both agents
        harness.observe("anxious", TENANT, "unexpected experimental result");
        harness.tick("anxious", TENANT);

        harness.observe("steady", TENANT, "unexpected experimental result");
        harness.tick("steady", TENANT);

        // Anxious agent should narrow focus (higher convergence)
        var anxiousBudget = harness.searchBudget("anxious", TENANT);
        var steadyBudget = harness.searchBudget("steady", TENANT);

        assertThat(anxiousBudget).isPresent();
        assertThat(steadyBudget).isPresent();

        assertThat(anxiousBudget.get().convergence())
            .as("anxious agent should have higher convergence (narrowing)")
            .isGreaterThan(steadyBudget.get().convergence());

        assertThat(steadyBudget.get().breadth())
            .as("steady agent should have higher breadth (broader exploration)")
            .isGreaterThan(anxiousBudget.get().breadth());

        // Both produce valid prompt text
        assertThat(harness.renderPrompt("anxious", TENANT)).isNotNull().isNotEmpty();
        assertThat(harness.renderPrompt("steady", TENANT)).isNotNull().isNotEmpty();

        // All budget values in valid range [0,1]
        for (var budget : List.of(anxiousBudget.get(), steadyBudget.get())) {
            assertThat(budget.breadth()).isBetween(0.0, 1.0);
            assertThat(budget.laterality()).isBetween(0.0, 1.0);
            assertThat(budget.depth()).isBetween(0.0, 1.0);
            assertThat(budget.convergence()).isBetween(0.0, 1.0);
        }
    }

    // Helper: construct NodeInput with name, subgraph type, and confidence
    // Implementer: check NodeInput constructor signature. Key fields: name (String),
    // subgraphType for routing, confidence (double). Look at existing tests
    // (RecurringThoughtIntegrationTest, CuriosityDriveTest) for the pattern.
    private NodeInput nodeInput(String name, String subgraphType, double confidence) {
        // TODO: fill in from NodeInput constructor
        throw new UnsupportedOperationException("implement from NodeInput API");
    }

    private EdgeInput edgeInput(String sourceId, String targetId, String type) {
        // TODO: fill in from EdgeInput constructor
        throw new UnsupportedOperationException("implement from EdgeInput API");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=CognitiveExplorationScenarioTest#s1_personalityDifferentiation -Dsurefire.failIfNoSpecifiedTests=false`
Expected: Compilation error — `CognitionTestHarness` does not exist.

- [ ] **Step 3: Create ConsolidationMediatorTestAccess**

This bridge class lives in the `core` package (same as `ConsolidationMediator`) so it can access the package-private `onConsolidation()` method. The harness in `eval` package calls this bridge.

```java
package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.mindmap.intelligence.consolidation.ConsolidationPhase;

import java.util.List;

public class ConsolidationMediatorTestAccess {

    public static void pushArtifacts(
            ConsolidationMediator mediator,
            String tenantId,
            List<? extends ConsolidationPhase> phases) {
        for (var phase : phases) {
            var artifacts = phase.artifacts();
            if (!artifacts.isEmpty()) {
                mediator.onConsolidation(
                    new ConsolidationCompleted(tenantId, List.of(), artifacts));
            }
        }
    }
}
```

Note: check the exact `ConsolidationCompleted` constructor. It may take `(String tenantId, List<PhaseResult> results, List<ConsolidationArtifact> artifacts)` or similar. Look at existing usages in `ConsolidationScheduler`.

- [ ] **Step 4: Create CognitionTestHarness**

This is the main deliverable. The harness has these logical parts:

**4a. Builder inner class** — accepts agent configurations (two paths: `agentFromAxes` for disposition-driven, `agentFromDefaults` for direct), optional store overrides, optional consolidation phases.

**4b. Component wiring in build()** — constructs all components with defaults:

Key wiring sequence:
1. Create or use provided `MindMapStore` (default: `new InMemoryMindMapStore()`)
2. Create or use provided `CaseMemoryStore` (default: `new InMemoryMemoryStore(mockPrincipal())` — mock `CurrentPrincipal` with test tenancy)
3. For each agent with `DispositionAxes`: call `CognitiveDerivationEngine.derive(DescriptorView.of(agentId, axes, List.of(), List.of()))` to get `CognitiveDefaults`
4. Build `CognitiveDefaultsRegistry.forTesting(allDefaults...)`
5. Create `InformationGapDetector(mindMapStore)`
6. Create `CuriosityChannelComputer()` (no-arg)
7. Create `CuriosityDrive(gapDetector, channelComputer, deprivationBias, goalNodeIdsSupplier)` — deprivationBias from first agent's CognitiveDefaults curiosity config, goalNodeIdsSupplier defaults to `() -> Set.of()`
8. Create stub `DriveSource` implementations for competence/affiliation/autonomy: `(agentId, tenantId) -> new DriveIntensity(axis, 0.1, "stub")`
9. Create `MoodOrchestrator(MoodConfig.defaults())`
10. Create `DriveOrchestrator(curiosityDrive, competenceStub, affiliationStub, autonomyStub, moodOrchestrator, new DriveComposer(), DriveConfig.defaults())` — check if `DriveConfig.defaults()` exists; if not, construct one
11. Create `ConsolidationMediator()` (no-arg)
12. Create `CognitionCore` via the 18-param constructor (pass null for unused orchestrators: UserModel, MentalModel, Strategy, Narrative, GoalProposal, MemoryHygiene, InnerLife, NeedTierMapping, AttentionMediator, engagement consumer, TemporalFocus, ReflectionRetrieval; pass `CognitionConfig.all()` and null for SessionPipeline)
13. Call `cognitionCore.setDefaultsRegistry(registry)`
14. Call `cognitionCore.configureSearchBudget(driveOrchestrator, moodOrchestrator, registry, mindMapStore, curiosityDrive)`
15. Call `cognitionCore.configureCuriosity(curiosityDrive, channelComputer, mindMapStore, deprivationBias, 0.5)`

**4c. Convenience methods:**

```java
public void observe(String agentId, String tenantId, String observation) {
    this.lastObservation.put(agentId, observation);
}

public void tick(String agentId, String tenantId) {
    var observation = lastObservation.remove(agentId);
    core.tick(agentId, tenantId, null, (a, t) -> Set.of(), observation, null);
}

public void consolidate(String agentId, String tenantId) {
    for (var phase : consolidationPhases) {
        phase.beginTick();
        phase.run(tenantId, List.of());
    }
    ConsolidationMediatorTestAccess.pushArtifacts(mediator, tenantId, consolidationPhases);
}

public void tickAndConsolidate(String agentId, String tenantId) {
    consolidate(agentId, tenantId);
    tick(agentId, tenantId);
}

public void setContinuumPosition(String agentId, Map<String, ContinuumPosition> positions) {
    // CognitionCore has volatile lastContinuumMap — need to set it
    // Option 1: reflection on the field
    // Option 2: push a RecurringThoughtDetected artifact via ConsolidationMediator
    //           then let the next tick() drain it
    // Prefer option 2 — push artifact, then tick drains it
}

public Optional<SearchBudget> searchBudget(String agentId, String tenantId) {
    return searchBudgetParticipant.currentBudget(agentId, tenantId);
}

public String renderPrompt(String agentId, String tenantId) {
    var context = new CognitionRenderContext(agentId, tenantId, null, List.of());
    return core.promptSections().stream()
        .map(s -> s.render(context))
        .filter(Objects::nonNull)
        .collect(Collectors.joining("\n\n"));
}

public MindMapStore mindMapStore() { return mindMapStore; }
public CaseMemoryStore memoryStore() { return memoryStore; }
public List<InformationGapScore> lastGaps(String agentId) { return curiosityDrive.lastGaps(); }
public CuriosityChannel lastChannel(String agentId) { return curiosityDrive.lastChannel(); }
public Optional<MoodState> currentMood(String agentId, String tenantId) {
    return moodOrchestrator.currentMood(agentId, tenantId);
}
```

**4d. Investigate AgentDescriptor handling:** `CognitionCore.tick()` takes `@Nullable AgentDescriptor`. Try passing null first. If `DriveOrchestrator.tick()` NPEs, create a minimal Mockito mock of `AgentDescriptor` that returns null for all methods. Register the mock in the harness per agent.

**4e. Fill in NodeInput/EdgeInput helpers in the test:** Look at `RecurringThoughtIntegrationTest` or `CuriosityDriveTest` for the exact `NodeInput` and `EdgeInput` constructor patterns. Key: NodeInput needs a name and the node goes into a subgraph; confidence is set either via constructor or via `store.updateNode()`.

- [ ] **Step 5: Run S1 test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=CognitiveExplorationScenarioTest#s1_personalityDifferentiation`
Expected: PASS

If the convergence/breadth differentiation assertions fail, the axis values may need tuning. The intent is contrasting profiles — try different `DispositionAxes` combinations until CDE produces sufficient differentiation. Check the derived `SearchBudgetConfig` for each agent via `registry.forAgentOrDefaults(agentId).searchBudgetConfig()`.

- [ ] **Step 6: Commit**

```bash
git add cognition/src/test/java/io/casehub/neocortex/cognition/eval/CognitionTestHarness.java cognition/src/test/java/io/casehub/neocortex/cognition/core/ConsolidationMediatorTestAccess.java cognition/src/test/java/io/casehub/neocortex/cognition/eval/CognitiveExplorationScenarioTest.java
git commit -m "feat(#849): add CognitionTestHarness + S1 personality differentiation scenario

Refs #849"
```

---

## Batch 2: Consolidation Support + Remaining Scenarios

After this batch: all 5 scenarios pass. Consolidation dual-mode (real phases + injection) is proven. Goal-directed curiosity, recurring thought escalation, channel shifts, and convergence collapse are all verified.

### Task 2: Consolidation Support + S3 Recurring Thought Escalation + S5 Convergence Collapse

S3 tests the full consolidation pipeline path (RecurringThoughtPhase → artifacts → tick → prompt modulation). S5 tests the injection bypass path (setContinuumPosition → tick → convergence collapse).

**Files:**
- Modify: `cognition/src/test/java/io/casehub/neocortex/cognition/eval/CognitionTestHarness.java` — add `tickAndConsolidate()`, `setContinuumPosition()`, `addExperiences()`, `continuumPosition()`
- Modify: `cognition/src/test/java/io/casehub/neocortex/cognition/eval/CognitiveExplorationScenarioTest.java` — add S3 and S5

**Interfaces:**
- Consumes: `RecurringThoughtPhase(CaseMemoryStore, MindMapStore, CognitiveDefaultsRegistry)` (package-private constructor), `ExperienceEvent.Observation` (record), `ConsolidationMediatorTestAccess.pushArtifacts()`, `RecurringThoughtDetected` (artifact type), `ContinuumPosition` (enum)
- Produces: Consolidation support on `CognitionTestHarness`; S3 + S5 test methods

- [ ] **Step 1: Write failing S3 test**

```java
@Test
void s3_recurringThoughtEscalation() {
    var store = new InMemoryMindMapStore();
    store.createSubgraph(TENANT, new SubgraphInput("concept"));
    store.addNode(TENANT, nodeInput("project-deadline", "concept", 0.5));

    // RecurringThoughtPhase needs CaseMemoryStore + MindMapStore + registry
    var harness = CognitionTestHarness.builder()
        .withMindMapStore(store)
        .agentFromDefaults("agent", CognitiveDefaults.empty("agent")
            .withRecurringThought(new RecurringThoughtConfig(
                Map.of(), 0.3, 0.6, 0.8, Duration.ofHours(24), 3)))
        .withConsolidationEnabled(true)
        .build();

    // Track continuum progression
    var positions = new ArrayList<ContinuumPosition>();
    boolean reachedFixation = false;
    boolean reachedObsession = false;

    for (int tick = 0; tick < 15; tick++) {
        // Add negative-valence experience memories about the topic
        harness.addExperiences("agent", TENANT, List.of(
            observation("agent", TENANT, "project-deadline stress increasing"),
            observation("agent", TENANT, "deadline pressure is overwhelming")
        ));

        harness.tickAndConsolidate("agent", TENANT);

        var position = harness.continuumPosition("agent", "project-deadline");
        positions.add(position);

        if (position == ContinuumPosition.FIXATION) reachedFixation = true;
        if (position == ContinuumPosition.OBSESSION) reachedObsession = true;
    }

    // Must progress through continuum in order
    assertThat(reachedFixation)
        .as("should reach FIXATION during escalation")
        .isTrue();
    assertThat(reachedObsession)
        .as("should reach OBSESSION during escalation")
        .isTrue();

    // First position should be HEALTHY
    assertThat(positions.get(0)).isEqualTo(ContinuumPosition.HEALTHY);

    // FIXATION must come before OBSESSION
    int fixationIdx = positions.indexOf(ContinuumPosition.FIXATION);
    int obsessionIdx = positions.indexOf(ContinuumPosition.OBSESSION);
    assertThat(fixationIdx).isLessThan(obsessionIdx);

    // Prompt text shifts with continuum
    // Re-tick at current state to get prompt
    harness.tick("agent", TENANT);
    var prompt = harness.renderPrompt("agent", TENANT);
    assertThat(prompt).isNotNull().isNotEmpty();
    // Specific prompt content depends on ContinuumPosition — check for
    // modulated language (circling back / compulsive / intrusive)
}
```

- [ ] **Step 2: Run S3 to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=CognitiveExplorationScenarioTest#s3_recurringThoughtEscalation`
Expected: Compilation error — `tickAndConsolidate()`, `addExperiences()`, `continuumPosition()`, `withConsolidationEnabled()` do not exist.

- [ ] **Step 3: Add consolidation support to CognitionTestHarness**

**3a. Builder additions:**
- `withConsolidationEnabled(boolean)` — when true, `build()` creates a `RecurringThoughtPhase(memoryStore, mindMapStore, registry)` and adds it to the phase list
- `withConsolidationPhase(ConsolidationPhase)` — for custom phases

**3b. addExperiences method:**

```java
public void addExperiences(String agentId, String tenantId, List<ExperienceEvent> events) {
    for (var event : events) {
        memoryStore.store(ExperienceEvents.toMemoryInput(event), tenantId);
    }
}
```

Check `ExperienceEvents.toMemoryInput()` for the conversion. If this is in the `memory` module (CDI), look at how `ExperienceRecorderCore` works and replicate the conversion inline.

**3c. setContinuumPosition method:**

For injection bypass, create a `RecurringThoughtDetected` artifact and push it through `ConsolidationMediator`:

```java
public void setContinuumPosition(String agentId, Map<String, ContinuumPosition> positions) {
    // Create RecurringThoughtDetected artifacts for each entry
    // Push via ConsolidationMediatorTestAccess
    // Next tick() will drain them into CognitionCore.lastContinuumMap
}
```

Check `RecurringThoughtDetected` constructor — it likely carries `(String agentId, Map<String, ContinuumPosition> positions)` or `(String agentId, String topic, ContinuumPosition position)`. Look at `RecurringThoughtPhase` to see how it creates these artifacts.

**3d. continuumPosition accessor:**

```java
public ContinuumPosition continuumPosition(String agentId, String topic) {
    // Read from CognitionCore.lastContinuumMap
    // This field is package-private volatile — may need reflection or
    // a test accessor in core package
    // Alternatively: read from the last RecurringThoughtPhase.artifacts()
}
```

**3e. observation helper:**

```java
// Static helper to create ExperienceEvent.Observation for test use
private static ExperienceEvent.Observation observation(
        String agentId, String tenantId, String description) {
    return new ExperienceEvent.Observation(
        agentId, tenantId, "test-case", "turn-" + UUID.randomUUID(),
        Instant.now(), description, null, Map.of(), "test-subject");
}
```

Check the `Observation` record constructor. Key fields: agentId, tenantId, caseId, turnId, timestamp, description. The `subject` field is required non-blank.

- [ ] **Step 4: Run S3 to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=CognitiveExplorationScenarioTest#s3_recurringThoughtEscalation`
Expected: PASS

If continuum doesn't escalate: check RecurringThoughtScorer threshold values in the `RecurringThoughtConfig` — the config's `fixationThreshold`, `obsessionThreshold`, and `ruminationThreshold` control the transitions. May need to add more memories per tick or adjust thresholds.

- [ ] **Step 5: Write S5 test (convergence collapse)**

```java
@Test
void s5_convergenceCollapse() {
    var harness = CognitionTestHarness.builder()
        .agentFromDefaults("agent", CognitiveDefaults.empty("agent")
            .withSearchBudget(SearchBudgetConfig.defaults()
                .withPathologicalThreshold(1.2))
            .withHabituation(new HabituationConfig(
                0.1, 0.3, 0.8, Map.of())))
        .build();

    // Inject OBSESSION continuum position (bypass consolidation)
    harness.setContinuumPosition("agent", Map.of("topic", ContinuumPosition.OBSESSION));

    // Set high arousal via mood
    harness.recordMood("agent", TENANT, 0.0, 0.8, 0.0);

    // Single tick
    harness.tick("agent", TENANT);

    // Convergence should collapse to 0.0
    var budget = harness.searchBudget("agent", TENANT);
    assertThat(budget).isPresent();
    assertThat(budget.get().convergence())
        .as("convergence should collapse under pathological threshold")
        .isEqualTo(0.0);

    // Other dimensions remain non-zero
    assertThat(budget.get().breadth()).isGreaterThan(0.0);
    assertThat(budget.get().depth()).isGreaterThan(0.0);
    assertThat(budget.get().laterality()).isGreaterThan(0.0);

    // Prompt reflects perseverative state
    var prompt = harness.renderPrompt("agent", TENANT);
    assertThat(prompt).isNotNull().isNotEmpty();
}
```

Note: `recordMood` is a convenience on the harness:
```java
public void recordMood(String agentId, String tenantId, double pleasure, double arousal, double dominance) {
    moodOrchestrator.record(new MoodSignal.DirectShift(pleasure, arousal, dominance, "test"), agentId, tenantId);
    moodOrchestrator.tick(agentId, tenantId);
}
```

Check `MoodSignal.DirectShift` constructor and `SearchBudgetConfig.withPathologicalThreshold()` — if `withPathologicalThreshold` doesn't exist, construct a new `SearchBudgetConfig` with the desired value directly.

- [ ] **Step 6: Run S5 to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=CognitiveExplorationScenarioTest#s5_convergenceCollapse`
Expected: PASS

The pathological threshold triggers when `arousalScore + (habituationFailure ? 1.0 : 0.0) > threshold`. With arousal 0.8 and habituationFailure true (from high repetitionTolerance in HabituationConfig): `0.8 + 1.0 = 1.8 > 1.2` → convergence collapses to 0.0.

- [ ] **Step 7: Run all tests to verify no regressions**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=CognitiveExplorationScenarioTest`
Expected: S1, S3, S5 all PASS.

- [ ] **Step 8: Commit**

```bash
git add cognition/src/test/java/io/casehub/neocortex/cognition/eval/CognitionTestHarness.java cognition/src/test/java/io/casehub/neocortex/cognition/eval/CognitiveExplorationScenarioTest.java
git commit -m "feat(#849): add consolidation support, S3 recurring thought escalation, S5 convergence collapse

Refs #849"
```

### Task 3: S2 Goal-Directed Exploration + S4 Curiosity Channel Shifts

These scenarios use the harness as-is — no new harness methods needed. S2 tests goal-directed gap prioritization. S4 tests curiosity channel transitions from DIVERSIVE to SPECIFIC.

**Files:**
- Modify: `cognition/src/test/java/io/casehub/neocortex/cognition/eval/CognitionTestHarness.java` — add `withGoalNodeIds(Supplier)` to builder if not already present
- Modify: `cognition/src/test/java/io/casehub/neocortex/cognition/eval/CognitiveExplorationScenarioTest.java` — add S2 and S4

**Interfaces:**
- Consumes: `CognitionTestHarness` (from Task 1), `lastGaps()`, `lastChannel()`, `searchBudget()`, `renderPrompt()`
- Produces: S2 + S4 test methods (final deliverables)

- [ ] **Step 1: Write S2 test (goal-directed exploration)**

```java
@Test
void s2_goalDirectedExploration() {
    var store = new InMemoryMindMapStore();
    store.createSubgraph(TENANT, new SubgraphInput("goal"));
    store.createSubgraph(TENANT, new SubgraphInput("research"));

    // Goal node
    var goalNode = store.addNode(TENANT, nodeInput("research-goal", "goal", 0.9));

    // 2 topic nodes at distance 1 from goal
    var near1 = store.addNode(TENANT, nodeInput("near-topic-1", "research", 0.45));
    var near2 = store.addNode(TENANT, nodeInput("near-topic-2", "research", 0.50));
    store.addEdge(TENANT, edgeInput(goalNode, near1, "enables"));
    store.addEdge(TENANT, edgeInput(goalNode, near2, "enables"));

    // 2 topic nodes at distance 2 (linked to near topics, not goal)
    var mid1 = store.addNode(TENANT, nodeInput("mid-topic-1", "research", 0.45));
    var mid2 = store.addNode(TENANT, nodeInput("mid-topic-2", "research", 0.50));
    store.addEdge(TENANT, edgeInput(near1, mid1, "relates-to"));
    store.addEdge(TENANT, edgeInput(near2, mid2, "relates-to"));

    // 3 unrelated nodes (no path to goal)
    var far1 = store.addNode(TENANT, nodeInput("unrelated-1", "research", 0.45));
    var far2 = store.addNode(TENANT, nodeInput("unrelated-2", "research", 0.50));
    var far3 = store.addNode(TENANT, nodeInput("unrelated-3", "research", 0.55));
    store.addEdge(TENANT, edgeInput(far1, far2, "relates-to"));
    store.addEdge(TENANT, edgeInput(far2, far3, "relates-to"));

    var harness = CognitionTestHarness.builder()
        .withMindMapStore(store)
        .agentFromDefaults("explorer", CognitiveDefaults.empty("explorer"))
        .withGoalNodeIds(() -> Set.of(goalNode))
        .build();

    harness.observe("explorer", TENANT, "exploring research landscape");
    harness.tick("explorer", TENANT);

    var gaps = harness.lastGaps("explorer");
    assertThat(gaps).isNotEmpty();

    // Distance-1 gaps should have goalRelevance 1.0
    var nearGaps = gaps.stream()
        .filter(g -> g.nodeName().startsWith("near-topic"))
        .toList();
    assertThat(nearGaps).isNotEmpty();
    nearGaps.forEach(g ->
        assertThat(g.goalRelevance()).as("distance-1 gap: " + g.nodeName()).isEqualTo(1.0));

    // Distance-2 gaps should have goalRelevance 0.7
    var midGaps = gaps.stream()
        .filter(g -> g.nodeName().startsWith("mid-topic"))
        .toList();
    assertThat(midGaps).isNotEmpty();
    midGaps.forEach(g ->
        assertThat(g.goalRelevance()).as("distance-2 gap: " + g.nodeName()).isEqualTo(0.7));

    // Unrelated gaps should have goalRelevance 0.0
    var farGaps = gaps.stream()
        .filter(g -> g.nodeName().startsWith("unrelated"))
        .toList();
    assertThat(farGaps).isNotEmpty();
    farGaps.forEach(g ->
        assertThat(g.goalRelevance()).as("unrelated gap: " + g.nodeName()).isEqualTo(0.0));

    // Goal-relevant gaps should rank higher (goal boost reshapes ordering)
    var topGap = gaps.get(0);
    assertThat(topGap.goalRelevance())
        .as("top-ranked gap should be goal-relevant")
        .isGreaterThan(0.0);

    // Prompt should contain goal-relevant annotation
    var prompt = harness.renderPrompt("explorer", TENANT);
    assertThat(prompt).contains("[goal-relevant]");
}
```

- [ ] **Step 2: Run S2 to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=CognitiveExplorationScenarioTest#s2_goalDirectedExploration`
Expected: PASS

If `lastGaps()` returns per-CuriosityDrive (shared), not per-agent: the harness may need per-agent CuriosityDrive instances. Check if CuriosityDrive maintains per-agent state internally or if it's stateless per `evaluate()` call. If shared state, refactor harness to create per-agent CuriosityDrive instances.

- [ ] **Step 3: Write S4 test (curiosity channel shifts)**

```java
@Test
void s4_curiosityChannelShifts() {
    var store = new InMemoryMindMapStore();

    // Start with many small partial-knowledge nodes → diversive territory
    store.createSubgraph(TENANT, new SubgraphInput("research"));
    store.createSubgraph(TENANT, new SubgraphInput("concept"));
    store.createSubgraph(TENANT, new SubgraphInput("project"));

    var smallNodes = new ArrayList<String>();
    // 8 small-gap nodes across 3 subgraphs
    for (int i = 0; i < 3; i++) {
        var n = store.addNode(TENANT, nodeInput("research-" + i, "research", 0.55));
        smallNodes.add(n);
    }
    for (int i = 0; i < 3; i++) {
        var n = store.addNode(TENANT, nodeInput("concept-" + i, "concept", 0.50));
        smallNodes.add(n);
    }
    for (int i = 0; i < 2; i++) {
        var n = store.addNode(TENANT, nodeInput("project-" + i, "project", 0.55));
        smallNodes.add(n);
    }
    // Add some edges so InformationGapDetector registers them
    for (int i = 0; i < smallNodes.size() - 1; i++) {
        store.addEdge(TENANT, edgeInput(smallNodes.get(i), smallNodes.get(i + 1), "relates-to"));
    }

    var harness = CognitionTestHarness.builder()
        .withMindMapStore(store)
        .agentFromDefaults("explorer", CognitiveDefaults.empty("explorer"))
        .build();

    // Tick 1: many small gaps → DIVERSIVE
    harness.observe("explorer", TENANT, "surveying the landscape");
    harness.tick("explorer", TENANT);

    var channel1 = harness.lastChannel("explorer");
    assertThat(channel1).isNotNull();
    assertThat(channel1.focus())
        .as("many small gaps should produce DIVERSIVE focus")
        .isEqualTo(FocusType.DIVERSIVE);

    var budget1 = harness.searchBudget("explorer", TENANT);
    assertThat(budget1).isPresent();
    assertThat(budget1.get().breadth())
        .as("DIVERSIVE: breadth should exceed depth")
        .isGreaterThan(budget1.get().depth());

    // Remove most small nodes, add one deep-gap node
    // Note: MindMapStore may not support removeNode directly.
    // Alternative: supersede the nodes or create a fresh store.
    // Check if InMemoryMindMapStore has a remove/supersede API.
    // If not, rebuild the store with only the deep node.
    // Simplest: build a new harness with different graph state.
    var store2 = new InMemoryMindMapStore();
    store2.createSubgraph(TENANT, new SubgraphInput("research"));
    var deepNode = store2.addNode(TENANT, nodeInput("deep-topic", "research", 0.2));
    // High edge count = many connections = deep gap
    for (int i = 0; i < 6; i++) {
        var connected = store2.addNode(TENANT, nodeInput("connected-" + i, "research", 0.8));
        store2.addEdge(TENANT, edgeInput(deepNode, connected, "relates-to"));
    }

    var harness2 = CognitionTestHarness.builder()
        .withMindMapStore(store2)
        .agentFromDefaults("explorer", CognitiveDefaults.empty("explorer"))
        .build();

    // Tick 2: single deep gap → SPECIFIC
    harness2.observe("explorer", TENANT, "diving deep into one topic");
    harness2.tick("explorer", TENANT);

    var channel2 = harness2.lastChannel("explorer");
    assertThat(channel2).isNotNull();
    assertThat(channel2.focus())
        .as("single deep gap should produce SPECIFIC focus")
        .isEqualTo(FocusType.SPECIFIC);

    var budget2 = harness2.searchBudget("explorer", TENANT);
    assertThat(budget2).isPresent();
    assertThat(budget2.get().depth())
        .as("SPECIFIC: depth should exceed breadth")
        .isGreaterThan(budget2.get().breadth());
}
```

Note: S4 uses two harness instances with different graph states rather than mutating between ticks. This is simpler than removing nodes and avoids API uncertainty around node deletion.

- [ ] **Step 4: Run S4 to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=CognitiveExplorationScenarioTest#s4_curiosityChannelShifts`
Expected: PASS

If channel doesn't shift: check `CuriosityChannelComputer.compute()` logic. The DIVERSIVE/SPECIFIC classification depends on gap distribution — many similar-sized gaps → DIVERSIVE, one dominant gap → SPECIFIC. The gap landscape in the test may need tuning (more contrast between the two states).

- [ ] **Step 5: Run all 5 scenarios**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=CognitiveExplorationScenarioTest`
Expected: All 5 tests PASS (s1, s2, s3, s4, s5).

- [ ] **Step 6: Run full cognition module test suite**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition`
Expected: All existing tests pass. No regressions. The pre-existing RegulationPipelineThroughTickTest failure (3 tests in caps-llm-classifier) is not in this module.

- [ ] **Step 7: Commit**

```bash
git add cognition/src/test/java/io/casehub/neocortex/cognition/eval/CognitionTestHarness.java cognition/src/test/java/io/casehub/neocortex/cognition/eval/CognitiveExplorationScenarioTest.java
git commit -m "feat(#849): add S2 goal-directed, S4 channel shifts scenarios

Closes #849"
```

- [ ] **Step 8: Create follow-up issue for test migration**

Create a GitHub issue to migrate existing manually-wired cognitive tests to `CognitionTestHarness`:

```bash
gh issue create --repo casehubio/neocortex --title "Migrate existing cognitive tests to CognitionTestHarness" --body "## Context

#849 introduced CognitionTestHarness — a fluent builder for cognitive pipeline testing. The harness consolidates store setup, component wiring, and prompt rendering into a reusable utility.

## Migration Candidates

- RecurringThoughtIntegrationTest
- SearchBudgetPathologicalThresholdTest
- CuriosityDriveTest / CuriosityQualityTest
- ContinuumRenderingTest

## Scope

Migrate each test to use CognitionTestHarness.builder() instead of manual wiring. If cross-module reuse is needed, extract the harness to a cognition-testing module.

Scale: S | Complexity: Low" --label "scale: S" --label "complexity: Low"
```

---

## References

- [2026-11-08-empirical-scenario-testing-design.md] — design spec this plan implements
- [849-decisions.md] — D1 (builder architecture), D2 (consolidation dual-mode), D3 (personality dual-mode)
- [cognition/src/main/java/.../core/CognitionCore.java] — 18-param constructor, tick(), configureSearchBudget(), configureCuriosity()
- [cognition/src/main/java/.../search/SearchBudgetTickParticipant.java] — 7-param constructor, currentBudget()
- [cognition/src/main/java/.../drive/CuriosityDrive.java] — 4-param constructor, lastGaps(), lastChannel()
- [cognition/src/main/java/.../drive/DriveOrchestrator.java] — 7-param constructor
- [cognition/src/main/java/.../mood/MoodOrchestrator.java] — MoodConfig.defaults(), record(), tick()
- [mindmap-intelligence/.../InformationGapDetector.java] — gap detection with BFS goal proximity
- [mindmap-intelligence/.../RecurringThoughtScorer.java] — seven-symptom composite classifier
- [mindmap-intelligence/.../consolidation/RecurringThoughtPhase.java] — package-private 3-param constructor
- [cognitive-index/.../CognitiveDerivationEngine.java] — derive(DescriptorView) static method
- [cognitive-index/.../CognitiveDefaultsRegistry.java] — forTesting() factory
- [cognitive-index/.../DescriptorView.java] — of() convenience factory
- [caps-testing/.../CognitiveEmergenceTest.java] — fluent scenario builder pattern (prior art)
- [cognition/src/test/.../RecurringThoughtIntegrationTest.java] — NodeInput/EdgeInput/store setup pattern
- casehubio/neocortex#849 — focal issue
- casehubio/neocortex#846 — parent epic
