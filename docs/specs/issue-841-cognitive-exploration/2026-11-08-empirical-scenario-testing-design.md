# Empirical Cognitive Exploration Testing — Design Spec

**Issue:** casehubio/neocortex#849
**Parent:** casehubio/neocortex#846
**Depends on:** #847 (curiosity→search budget coupling), #848 (goal-directed curiosity)
**Date:** 2026-11-08

## Problem

The cognitive exploration pipeline (curiosity + search budget + recurring thought continuum) has unit tests and component-level integration tests, but no end-to-end empirical verification. Each component is tested in isolation — CuriosityDrive, SearchBudgetTickParticipant, RecurringThoughtScorer, prompt sections — but there is no test that wires the full pipeline and asserts that specific agent profiles and stimuli sequences produce the expected cognitive state descriptions.

Existing tests also suffer from duplicated wiring. RecurringThoughtIntegrationTest, SearchBudgetPathologicalThresholdTest, CuriosityDriveTest, and ContinuumRenderingTest each re-invent store setup, component construction, and result extraction. When CognitionCore's constructor changes, multiple tests break independently.

## Solution

A general-purpose `CognitionTestHarness` with a fluent builder that wires the cognitive pipeline with sensible defaults (in-memory stores, no-op orchestrators, no LLM). Five empirical scenarios (S1–S5) verify integrated pipeline behavior. A follow-up issue migrates existing manually-wired tests to the harness once it's proven.

## Architecture

### CognitionTestHarness

The harness wraps `CognitionCore` + stores + consolidation phases. Constructed exclusively via `HarnessBuilder`. Two execution modes:

1. **Full pipeline:** `tickAndConsolidate()` runs consolidation phases → pushes artifacts to `ConsolidationMediator` → runs `CognitionCore.tick()` (which drains artifacts, populates `lastContinuumMap`, runs all four cognitive phases)
2. **Injection bypass:** `setContinuumPosition()` / `injectArtifact()` write state directly, then `tick()` runs CognitionCore without consolidation

### Component Wiring (Defaults)

| Component | Default | Override |
|-----------|---------|----------|
| MindMapStore | InMemoryMindMapStore | `withMindMapStore()` |
| CaseMemoryStore | InMemoryMemoryStore | `withMemoryStore()` |
| MoodOrchestrator | Real, default MoodConfig | Per-agent via CognitiveDefaults |
| DriveOrchestrator | Real, 4 drive sources | Per-agent via CognitiveDefaults |
| CuriosityDrive | Real, with InformationGapDetector | Per-agent (deprivationBias, goalNodeIds) |
| CompetenceDrive / AffiliationDrive / AutonomyDrive | Stub returning constant `DriveIntensity(0.1)` — present but non-dominant in drive composition | Not overridable |
| SearchBudgetTickParticipant | Real | Per-agent via CognitiveDefaults |
| CognitiveDefaultsRegistry | Built from agent configs | Automatic from builder |
| ConsolidationMediator | Real | Not overridable |
| Consolidation phases | None | `withConsolidationPhase()` |
| CognitionConfig | All subsystems enabled | `withCognitionConfig()` |
| SessionPipeline | null (no LLM) | Not overridable |
| SubjectResolver | No-op (returns empty) | Not overridable |

### Agent Configuration (Dual-Mode)

**Disposition path** — CognitiveDerivationEngine derives CognitiveDefaults from DispositionAxes:
```java
.agent("anxious", new DispositionAxes("reserved", "flexible", "cautious", "low", "avoidant"))
```
DispositionAxes fields are `(String socialOrient, String ruleFollowing, String riskAppetite, String autonomy, String conflictMode)`. The builder constructs a `DescriptorView` from the axes (with empty disposition profile, goals, null FormationPadSummary, empty facets), calls `CognitiveDerivationEngine.derive()` to produce `CognitiveDefaults`, and registers the result in `CognitiveDefaultsRegistry.forTesting()`.

**Direct path** — explicit CognitiveDefaults:
```java
.agent("test", CognitiveDefaults.empty("test")
    .withSearchBudget(searchBudgetConfig)
    .withHabituation(habituationConfig))
```
The builder registers the defaults directly. No `DescriptorView` needed — `CognitionCore.tick()` is called with null `AgentDescriptor` since the registry already has the config.

### Builder API

```java
CognitionTestHarness harness = CognitionTestHarness.builder()
    .agent("anxious", new DispositionAxes("reserved", "flexible", "cautious", "low", "avoidant"))
    .agent("test", CognitiveDefaults.empty("test").withSearchBudget(config))
    .withMindMapStore(customStore)                    // optional override
    .withConsolidationPhase(recurringThoughtPhase)    // optional
    .withCognitionConfig(cognitionConfig)              // optional
    .build();
```

### Convenience Methods

```java
// Store access (for graph/memory setup in tests)
harness.mindMapStore()
harness.memoryStore()

// Observation + experience helpers
harness.observe(agentId, tenantId, "observation text")
harness.addExperiences(agentId, tenantId, List.of(event1, event2))

// Pipeline execution
harness.tick(agentId, tenantId)                // CognitionCore.tick() only
harness.consolidate(agentId, tenantId)         // consolidation phases only
harness.tickAndConsolidate(agentId, tenantId)  // consolidate → tick

// State injection (bypass consolidation)
harness.setContinuumPosition(agentId, Map.of("topic", OBSESSION))

// State access (for assertions)
harness.searchBudget(agentId, tenantId)      → Optional<SearchBudget>
harness.continuumPosition(agentId, topic)    → ContinuumPosition
harness.lastGaps(agentId)                    → List<InformationGapScore>
harness.lastChannel(agentId)                 → CuriosityChannel
harness.currentMood(agentId, tenantId)       → Optional<MoodState>

// Prompt rendering
harness.renderPrompt(agentId, tenantId)       → String (all sections joined)
harness.renderSection(agentId, tenantId, cls) → String (specific section by class)
```

### Tick Execution Flow

`tickAndConsolidate(agentId, tenantId)`:

1. Run configured consolidation phases in `@Priority` order (ascending)
2. Collect `ConsolidationArtifact`s via `phase.artifacts()` (drain semantics)
3. Push artifacts to `ConsolidationMediator`
4. Construct `CognitionRenderContext(agentId, tenantId, null, List.of())`
5. Call `CognitionCore.tick(agentId, tenantId, null, noOpResolver, lastObservation, null)` — `AgentDescriptor` is null; per-agent configuration is already in `CognitiveDefaultsRegistry`
   - Tick drains `ConsolidationMediator` → populates `lastContinuumMap` from `RecurringThoughtDetected` artifacts
   - FOUNDATION: mood tick
   - SOURCE: drive tick (CuriosityDrive.evaluate → gap detection → channel computation)
   - DERIVED: SearchBudgetTickParticipant.tick (reads drives + channel + mood → 4D budget)
   - TERMINAL: prompt section assembly

`tick(agentId, tenantId)`: Steps 4-5 only (no consolidation).

**AgentDescriptor:** Passed as null to `CognitionCore.tick()`. The harness pre-registers all agent configuration in `CognitiveDefaultsRegistry` at build time. For the disposition path, the builder calls `CognitiveDerivationEngine.derive(DescriptorView.of(agentId, axes, List.of(), List.of()))` during construction, registers the result, then discards the DescriptorView. CognitionCore looks up the registry by agentId during tick.

### Multi-Tick Scenarios

Tests call execution methods in a loop, modifying store state between ticks:

```java
for (int i = 0; i < 10; i++) {
    harness.addExperiences(AGENT, TENANT, negativeExperiences("project-deadline"));
    harness.tickAndConsolidate(AGENT, TENANT);
    // assert on intermediate state
}
```

## Scenarios

### S1: Personality Differentiation

**Goal:** Verify that different dispositions produce measurably different search budgets from the same stimuli.

**Setup:**
- Agent "anxious": `DispositionAxes("reserved", "flexible", "cautious", "low", "avoidant")` — withdrawn, rule-averse, risk-averse profile; CDE should derive higher neuroticism-equivalent personality weights, narrower search budget
- Agent "steady": `DispositionAxes("cooperative", "strict", "measured", "high", "analytical")` — engaged, disciplined, autonomous profile; CDE should derive higher conscientiousness-equivalent weights, broader search budget
- Shared InMemoryMindMapStore with research subgraph: 3-4 nodes, partial knowledge (confidence 0.4-0.6)
- Exact axis values may need tuning during implementation if CDE derivation doesn't produce sufficient differentiation — the intent is contrasting cognitive profiles, not specific axis values

**Execution:** One `tickAndConsolidate()` per agent with the same observation.

**Assertions:**
- Anxious agent: `convergence > steady.convergence` (narrowing focus)
- Steady agent: `breadth > anxious.breadth` (broader exploration)
- Both produce non-null, non-empty prompt text
- Search budget parameter ranges are valid (all in [0,1])

### S2: Goal-Directed Exploration

**Goal:** Verify that curiosity prioritises gaps near active goals.

**Setup:**
- Single agent, moderate personality
- GOAL subgraph: 1 goal node
- RESEARCH subgraph: 2 topic nodes linked to goal (distance 1), 2 at distance 2, 3 unrelated (no path)
- All topic nodes: partial knowledge (confidence 0.4-0.6, 2-3 edges)
- CuriosityDrive constructed with `activeGoalNodeIds` returning the goal node ID

**Execution:** One `tickAndConsolidate()`.

**Assertions:**
- `lastGaps()`: distance-1 gaps have `goalRelevance == 1.0`, distance-2 have `goalRelevance == 0.7`, unrelated have `goalRelevance == 0.0`
- Gap ordering: goal-relevant gaps rank above equally-intense non-goal gaps (goal boost reshapes ranking)
- Search budget: depth elevated (focused exploration mode)
- Prompt text: contains `[goal-relevant]` annotation (from CuriosityDrive.formatTrigger(), added in #848)

### S3: Recurring Thought Escalation

**Goal:** Verify that repeated negative-valence observations about the same topic escalate through the continuum: HEALTHY → FIXATION → OBSESSION.

**Setup:**
- Single agent
- MindMap: node "project-deadline" in CONCEPT subgraph
- `RecurringThoughtPhase` configured as consolidation phase
- CaseMemoryStore starts empty

**Execution:** 10-15 ticks. Each tick:
1. Add 2-3 negative-valence experience memories about "project-deadline"
2. Call `tickAndConsolidate()`
3. Assert on `continuumPosition()`

**Assertions (progression):**

Exact tick numbers where transitions occur depend on scorer thresholds and memory accumulation rates. The test asserts that transitions happen in order and that specific positions are reached — not that they happen at exact tick N. Approximate windows:

- Early ticks: `continuumPosition() == HEALTHY` — below fixation threshold
- Mid ticks: `continuumPosition() == FIXATION` — threshold crossed
- Late ticks: `continuumPosition() == OBSESSION` — escalation continues
- Prompt text shifts:
  - HEALTHY → normal curiosity language
  - FIXATION → contains "circling back" or urgency framing
  - OBSESSION → contains "compulsive" or intrusive framing

### S4: Curiosity Channel Shifts

**Goal:** Verify that curiosity channels shift from DIVERSIVE to SPECIFIC as the gap landscape changes.

**Setup:**
- Single agent, moderate personality
- MindMap starts with 8-10 small partial-knowledge nodes across 3+ subgraphs (many small gaps → diversive territory)

**Execution:** 3-4 ticks.
- Tick 1: many small gaps → assert DIVERSIVE channel
- Between ticks: remove most small-gap nodes, add one deep-gap node (low confidence 0.2, high edge count 6+)
- Tick 2: single deep gap dominates → assert SPECIFIC channel

**Assertions:**
- `lastChannel()`: transitions from DIVERSIVE (or BLENDED) to SPECIFIC
- Search budget: breadth decreases, depth increases across ticks
- Channel modulation: DIVERSIVE tick shows `breadth > depth`; SPECIFIC tick shows `depth > breadth`
- Prompt text: shifts from broad exploration language to focused investigation language

### S5: Convergence Collapse

**Goal:** Verify that high arousal + habituation failure triggers pathological convergence collapse.

**Setup:**
- Single agent via direct `CognitiveDefaults`:
  - `SearchBudgetConfig` with low `pathologicalThreshold` (e.g., 1.2)
  - `HabituationConfig` with `repetitionTolerance` set so `habituationFailure == true`
- Inject `setContinuumPosition(agent, Map.of("topic", OBSESSION))` — bypass consolidation
- Set mood to high arousal via MoodOrchestrator

**Execution:** Single `tick()`.

**Assertions:**
- `searchBudget().convergence() == 0.0` — pathological collapse (`arousalScore + 1.0 > 1.2`)
- Other budget dimensions (breadth, depth, laterality) remain non-zero
- Prompt text: SearchBudgetPromptSection modulated by OBSESSION — perseverative language

## Module Structure

**Package:** `io.casehub.neocortex.cognition.eval`
**Location:** `cognition/src/test/java/io/casehub/neocortex/cognition/eval/`

```
CognitionTestHarness.java            — builder + convenience methods (~200-300 lines)
CognitiveExplorationScenarioTest.java — S1-S5 (~40 lines per scenario, ~250 total)
```

**Test dependencies (cognition/pom.xml, test scope):**

All required dependencies are already present:
- `casehub-neocortex-mindmap-inmem` — InMemoryMindMapStore
- `casehub-neocortex-memory-inmem` — InMemoryMemoryStore
- `casehub-neocortex-memory-cbr-inmem` — InMemoryCbrRecordStore
- `assertj-core`
- `mockito-core`

`mindmap-intelligence` (RecurringThoughtScorer, InformationGapDetector, CuriosityChannelComputer) is a compile dependency of cognition — transitively available in test scope.

## Assertions Approach

Standard AssertJ on harness state accessors. No custom assertion classes.

```java
assertThat(harness.searchBudget("anxious", TENANT))
    .isPresent()
    .get()
    .satisfies(b -> {
        assertThat(b.convergence()).isGreaterThan(0.7);
        assertThat(b.breadth()).isLessThan(0.4);
    });

assertThat(harness.renderPrompt("anxious", TENANT))
    .contains("narrowing")
    .doesNotContain("broad exploration");
```

## Follow-Up

After #849 lands, create an issue to migrate existing manually-wired cognitive tests to CognitionTestHarness:
- RecurringThoughtIntegrationTest
- SearchBudgetPathologicalThresholdTest
- CuriosityDriveTest / CuriosityQualityTest
- ContinuumRenderingTest

If cross-module reuse is needed, extract the harness to a `cognition-testing` module at that point.

## References

- casehubio/neocortex#849 — issue
- casehubio/neocortex#846 — parent epic
- casehubio/neocortex#847 — curiosity→search budget coupling (prerequisite)
- casehubio/neocortex#848 — goal-directed curiosity (prerequisite)
- `cognition/src/main/java/.../core/CognitionCore.java` — composition root
- `cognition/src/main/java/.../search/SearchBudgetTickParticipant.java` — 4D budget computation
- `cognition/src/main/java/.../drive/CuriosityDrive.java` — gap-based curiosity
- `mindmap-intelligence/src/main/java/.../InformationGapDetector.java` — gap detection with BFS goal proximity
- `mindmap-intelligence/src/main/java/.../RecurringThoughtScorer.java` — seven-symptom composite classifier
- `mindmap-intelligence/src/main/java/.../consolidation/RecurringThoughtPhase.java` — consolidation phase
- `cognitive-index/src/main/java/.../CognitiveDerivationEngine.java` — disposition→config derivation
- `cognitive-index/src/main/java/.../CognitiveDefaultsRegistry.java` — forTesting() factory
- `caps-testing/.../CognitiveEmergenceTest.java` — fluent scenario builder pattern (prior art)
- `849-decisions.md` — D1 (builder architecture), D2 (consolidation dual-mode), D3 (personality dual-mode)
