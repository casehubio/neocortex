# Curiosity → Search Budget Coupling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #847 — Curiosity → search budget coupling: channel data modulates exploration parameters
**Issue group:** #846, #847

**Goal:** Wire CuriosityChannel four-channel data into SearchBudgetTickParticipant so curiosity channels directly modulate the 4 search budget parameters, with per-agent configurable magnitudes derived from personality.

**Architecture:** CuriosityDrive caches computed CuriosityChannel alongside existing lastGaps(). A new ChannelModulationConfig record (8 magnitude fields, nullable on SearchBudgetConfig) controls per-rule strengths. SearchBudgetTickParticipant reads channel state and applies additive modulation after existing drive/mood/gut/threat computation. CDE derives magnitudes from PID-5 openness/conscientiousness/anxiousness.

**Tech Stack:** Java 21, Quarkus 3.32.2, JUnit 5, Mockito

## Global Constraints

- All new types in cognitive-api are tier 1 (zero deps)
- ChannelModulationConfig is nullable on SearchBudgetConfig — null means no channel modulation
- Channel modulation is additive, applied after existing computation, clamped to [0,1]
- CuriosityDrive.lastChannel() is volatile for thread safety
- Continuous gradient for comprehension/gapProximity, discrete for focus/hedonic type
- CognitionCore.configureSearchBudget() gains a @Nullable CuriosityDrive parameter — external callers (agency) must be updated separately

---

## Batch 1: Foundation types and state caching

### Task 1: Create ChannelModulationConfig and add to SearchBudgetConfig

**Files:**
- Create: `cognitive-api/src/main/java/io/casehub/neocortex/cognitive/ChannelModulationConfig.java`
- Modify: `cognitive-api/src/main/java/io/casehub/neocortex/cognitive/SearchBudgetConfig.java:23-61`
- Modify: `cognitive-api/src/test/java/io/casehub/neocortex/cognitive/SearchBudgetConfigTest.java`
- Create: `cognitive-api/src/test/java/io/casehub/neocortex/cognitive/ChannelModulationConfigTest.java`
- Modify: `cognitive-index/src/main/java/io/casehub/neocortex/cognitive/index/CognitiveDerivationEngine.java:678-688` (constructor call)

**Interfaces:**
- Produces: `ChannelModulationConfig` record with 8 `double` fields and `defaults()` factory
- Produces: `SearchBudgetConfig` gains `@Nullable ChannelModulationConfig channelModulation()` as 11th field

- [ ] **Step 1: Write ChannelModulationConfig test**

```java
// cognitive-api/src/test/java/io/casehub/neocortex/cognitive/ChannelModulationConfigTest.java
package io.casehub.neocortex.cognitive;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChannelModulationConfigTest {

    @Test
    void defaults_returns_values_in_expected_range() {
        var config = ChannelModulationConfig.defaults();
        assertTrue(config.specificDepthBoost() > 0 && config.specificDepthBoost() <= 0.25);
        assertTrue(config.specificBreadthPenalty() > 0 && config.specificBreadthPenalty() <= 0.25);
        assertTrue(config.diversiveBreadthBoost() > 0 && config.diversiveBreadthBoost() <= 0.25);
        assertTrue(config.diversiveLateralityBoost() > 0 && config.diversiveLateralityBoost() <= 0.25);
        assertTrue(config.diversiveConvergencePenalty() > 0 && config.diversiveConvergencePenalty() <= 0.25);
        assertTrue(config.deprivationConvergenceBoost() > 0 && config.deprivationConvergenceBoost() <= 0.25);
        assertTrue(config.lowComprehensionLateralityBoost() > 0 && config.lowComprehensionLateralityBoost() <= 0.25);
        assertTrue(config.highComprehensionConvergenceBoost() > 0 && config.highComprehensionConvergenceBoost() <= 0.25);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-api -Dtest=ChannelModulationConfigTest -DfailIfNoTests=false`
Expected: Compilation error — ChannelModulationConfig does not exist

- [ ] **Step 3: Create ChannelModulationConfig record**

Use `ide_create_file` or Write to create:

```java
// cognitive-api/src/main/java/io/casehub/neocortex/cognitive/ChannelModulationConfig.java
package io.casehub.neocortex.cognitive;

/**
 * Per-agent tuning for how curiosity channel state modulates search budget.
 * Each field controls the magnitude of one channel→budget modulation rule.
 * Magnitudes are additive offsets applied after base drive computation.
 *
 * @param specificDepthBoost                Berlyne SPECIFIC focus × gapProximity → +depth
 * @param specificBreadthPenalty            Berlyne SPECIFIC focus × gapProximity → −breadth
 * @param diversiveBreadthBoost             Berlyne DIVERSIVE focus → +breadth
 * @param diversiveLateralityBoost          Berlyne DIVERSIVE focus → +laterality
 * @param diversiveConvergencePenalty       Berlyne DIVERSIVE focus → −convergence
 * @param deprivationConvergenceBoost       Litman D_TYPE hedonic → +convergence
 * @param lowComprehensionLateralityBoost   Silvia (1−comprehension) → +laterality
 * @param highComprehensionConvergenceBoost Silvia comprehension → +convergence
 */
public record ChannelModulationConfig(
        double specificDepthBoost,
        double specificBreadthPenalty,
        double diversiveBreadthBoost,
        double diversiveLateralityBoost,
        double diversiveConvergencePenalty,
        double deprivationConvergenceBoost,
        double lowComprehensionLateralityBoost,
        double highComprehensionConvergenceBoost) {

    public static ChannelModulationConfig defaults() {
        return new ChannelModulationConfig(0.12, 0.08, 0.12, 0.10, 0.08, 0.10, 0.10, 0.10);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-api -Dtest=ChannelModulationConfigTest`
Expected: PASS

- [ ] **Step 5: Add channelModulation field to SearchBudgetConfig**

Use `ide_replace_member` to update the SearchBudgetConfig record. Add `@Nullable ChannelModulationConfig channelModulation` as the 11th field. Update the compact constructor to accept it (no validation needed — null means disabled). Update `defaults()` to pass `null`.

The record becomes:
```java
public record SearchBudgetConfig(
        double breadthSteepness,
        double breadthMidpoint,
        double lateralitySteepness,
        double lateralityMidpoint,
        double lateralityThreatFloor,
        double depthSteepness,
        double depthMidpoint,
        double convergenceSteepness,
        double convergenceMidpoint,
        double pathologicalThreshold,
        @Nullable ChannelModulationConfig channelModulation) {
    // existing compact constructor unchanged
    // add import for org.jspecify.annotations.Nullable

    public static SearchBudgetConfig defaults() {
        return new SearchBudgetConfig(6.0, 0.5, 6.0, 0.5, 0.7, 3.0, 0.5, 6.0, 0.5, 1.4, null);
    }
}
```

- [ ] **Step 6: Fix all existing SearchBudgetConfig constructor calls**

There are 4 existing constructor calls (3 in SearchBudgetConfigTest, 1 in CognitiveDerivationEngine) that need the 11th `null` argument appended:

`cognitive-api/src/test/java/io/casehub/neocortex/cognitive/SearchBudgetConfigTest.java` — 3 calls at lines 28, 34, 40: append `, null)` before the closing paren.

`cognitive-index/src/main/java/io/casehub/neocortex/cognitive/index/CognitiveDerivationEngine.java` line 678-688: append `, null)` to the positional constructor call.

- [ ] **Step 7: Build cognitive-api and cognitive-index to verify compilation**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn compile -pl cognitive-api,cognitive-index`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add cognitive-api/src/main/java/io/casehub/neocortex/cognitive/ChannelModulationConfig.java \
       cognitive-api/src/main/java/io/casehub/neocortex/cognitive/SearchBudgetConfig.java \
       cognitive-api/src/test/java/io/casehub/neocortex/cognitive/ChannelModulationConfigTest.java \
       cognitive-api/src/test/java/io/casehub/neocortex/cognitive/SearchBudgetConfigTest.java \
       cognitive-index/src/main/java/io/casehub/neocortex/cognitive/index/CognitiveDerivationEngine.java
git commit -m "feat(#847): add ChannelModulationConfig and wire into SearchBudgetConfig

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Add lastChannel() to CuriosityDrive

**Files:**
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/drive/CuriosityDrive.java:8-44`
- Modify: `cognition/src/test/java/io/casehub/neocortex/cognition/drive/CuriosityDriveTest.java`

**Interfaces:**
- Consumes: `CuriosityChannelComputer.compute(List<InformationGapScore>, double)` → `@Nullable CuriosityChannel`
- Produces: `CuriosityDrive.lastChannel()` → `@Nullable CuriosityChannel`

- [ ] **Step 1: Write the failing test**

Add a test to CuriosityDriveTest that verifies lastChannel() is populated after evaluate():

```java
@Test
void lastChannel_populated_after_evaluate() {
    var store = mock(MindMapStore.class);
    when(store.search(any())).thenReturn(List.of()); // no gaps
    var gapDetector = new InformationGapDetector(store);

    var channelComputer = new CuriosityChannelComputer();
    var drive = new CuriosityDrive(gapDetector, channelComputer, 0.5);

    assertNull(drive.lastChannel(), "Should be null before first evaluate");

    drive.evaluate("agent1", "tenant1");

    // With no gaps, channel should be null (CuriosityChannelComputer returns null for empty)
    assertNull(drive.lastChannel(), "No gaps → null channel");
}

@Test
void lastChannel_computed_when_gaps_present() {
    var node = mockNode("topic1", 0.7, 0.8, GapType.PARTIAL_KNOWLEDGE, "person");
    var store = mock(MindMapStore.class);
    when(store.search(any())).thenReturn(List.of(node));

    var gapDetector = new InformationGapDetector(store);
    var channelComputer = new CuriosityChannelComputer();
    var drive = new CuriosityDrive(gapDetector, channelComputer, 0.5);

    drive.evaluate("agent1", "tenant1");

    assertNotNull(drive.lastChannel(), "Should be non-null when gaps present");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=CuriosityDriveTest#lastChannel_populated_after_evaluate -DfailIfNoTests=false`
Expected: Compilation error — CuriosityDrive constructor doesn't accept channelComputer

- [ ] **Step 3: Update CuriosityDrive to compute and cache channel**

Use `ide_replace_member` to update the CuriosityDrive class. Add CuriosityChannelComputer + deprivationBias as constructor dependencies, add volatile lastChannel field, compute channel in evaluate():

```java
package io.casehub.neocortex.cognition.drive;

import io.casehub.neocortex.mindmap.intelligence.CuriosityChannelComputer;
import io.casehub.neocortex.mindmap.intelligence.InformationGapDetector;
import io.casehub.neocortex.mindmap.intelligence.InformationGapScore;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class CuriosityDrive implements DriveSource {

    private static final int TOP_N = 5;
    private final InformationGapDetector gapDetector;
    private final CuriosityChannelComputer channelComputer;
    private final double deprivationBias;
    private volatile List<InformationGapScore> lastGaps = List.of();
    private volatile @Nullable CuriosityChannel lastChannel;

    public CuriosityDrive(InformationGapDetector gapDetector,
                          CuriosityChannelComputer channelComputer,
                          double deprivationBias) {
        this.gapDetector = gapDetector;
        this.channelComputer = channelComputer;
        this.deprivationBias = deprivationBias;
    }

    @Override
    public DriveIntensity evaluate(String agentId, String tenantId) {
        var gaps = gapDetector.detectGaps(tenantId, null);
        this.lastGaps = gaps;
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
                                .map(g -> g.nodeName() + " (" + g.gapType().name().toLowerCase().replace('_', ' ') + ")")
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("unknown gaps");

        return new DriveIntensity(DriveAxis.CURIOSITY, intensity, trigger);
    }

    public List<InformationGapScore> lastGaps() {
        return lastGaps;
    }

    public @Nullable CuriosityChannel lastChannel() {
        return lastChannel;
    }
}
```

- [ ] **Step 4: Fix existing CuriosityDrive callers**

Search for existing `new CuriosityDrive(` calls using `ide_search_text`. Each must be updated to pass the new `channelComputer` and `deprivationBias` parameters. CuriosityPromptSection (in CognitionCore.configureCuriosity()) already has access to both — the caller creates CuriosityDrive with the same deprivationBias it passes to configureCuriosity().

Existing CuriosityDriveTest calls: update to pass `new CuriosityChannelComputer()` and a deprivationBias of `0.5`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=CuriosityDriveTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add cognition/src/main/java/io/casehub/neocortex/cognition/drive/CuriosityDrive.java \
       cognition/src/test/java/io/casehub/neocortex/cognition/drive/CuriosityDriveTest.java
git commit -m "feat(#847): add lastChannel() caching to CuriosityDrive

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Batch 2: Runtime coupling

### Task 3: Add channel modulation to SearchBudgetTickParticipant

**Files:**
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/search/SearchBudgetTickParticipant.java:38-223`
- Modify: `cognition/src/test/java/io/casehub/neocortex/cognition/search/SearchBudgetTickParticipantTest.java`

**Interfaces:**
- Consumes: `CuriosityDrive.lastChannel()` → `@Nullable CuriosityChannel`
- Consumes: `SearchBudgetConfig.channelModulation()` → `@Nullable ChannelModulationConfig`
- Produces: `SearchBudget` values now incorporate channel modulation

- [ ] **Step 1: Write failing tests for channel modulation**

Add tests to SearchBudgetTickParticipantTest:

```java
@Test
void specific_focus_high_gap_boosts_depth_reduces_breadth() {
    var channel = new CuriosityChannel(HedonicType.I_TYPE, 0.3, FocusType.SPECIFIC, 0.9);
    var participant = withChannelAndConfig(DriveAxis.CURIOSITY, 0.5, channel,
            ChannelModulationConfig.defaults());
    participant.tick(context());

    var budget = participant.currentBudget(AGENT, TENANT).orElseThrow();
    var baseline = baselineBudget(DriveAxis.CURIOSITY, 0.5);

    assertTrue(budget.depth() > baseline.depth(),
            "SPECIFIC + high gap proximity should boost depth: " + budget.depth() + " vs " + baseline.depth());
    assertTrue(budget.breadth() < baseline.breadth(),
            "SPECIFIC + high gap proximity should reduce breadth: " + budget.breadth() + " vs " + baseline.breadth());
}

@Test
void diversive_focus_boosts_breadth_laterality_reduces_convergence() {
    var channel = new CuriosityChannel(HedonicType.I_TYPE, 0.5, FocusType.DIVERSIVE, 0.5);
    var participant = withChannelAndConfig(DriveAxis.CURIOSITY, 0.5, channel,
            ChannelModulationConfig.defaults());
    participant.tick(context());

    var budget = participant.currentBudget(AGENT, TENANT).orElseThrow();
    var baseline = baselineBudget(DriveAxis.CURIOSITY, 0.5);

    assertTrue(budget.breadth() > baseline.breadth(),
            "DIVERSIVE should boost breadth");
    assertTrue(budget.laterality() > baseline.laterality(),
            "DIVERSIVE should boost laterality");
    assertTrue(budget.convergence() < baseline.convergence(),
            "DIVERSIVE should reduce convergence");
}

@Test
void d_type_hedonic_boosts_convergence() {
    var channel = new CuriosityChannel(HedonicType.D_TYPE, 0.5, FocusType.SPECIFIC, 0.5);
    var participant = withChannelAndConfig(DriveAxis.CURIOSITY, 0.5, channel,
            ChannelModulationConfig.defaults());
    participant.tick(context());

    var budget = participant.currentBudget(AGENT, TENANT).orElseThrow();
    var baseline = baselineBudget(DriveAxis.CURIOSITY, 0.5);

    assertTrue(budget.convergence() > baseline.convergence(),
            "D_TYPE should boost convergence");
}

@Test
void low_comprehension_boosts_laterality() {
    var channel = new CuriosityChannel(HedonicType.I_TYPE, 0.1, FocusType.DIVERSIVE, 0.5);
    var participant = withChannelAndConfig(DriveAxis.CURIOSITY, 0.5, channel,
            ChannelModulationConfig.defaults());
    participant.tick(context());

    var budgetLow = participant.currentBudget(AGENT, TENANT).orElseThrow();

    var channelHigh = new CuriosityChannel(HedonicType.I_TYPE, 0.9, FocusType.DIVERSIVE, 0.5);
    var participantHigh = withChannelAndConfig(DriveAxis.CURIOSITY, 0.5, channelHigh,
            ChannelModulationConfig.defaults());
    participantHigh.tick(context());

    var budgetHigh = participantHigh.currentBudget(AGENT, TENANT).orElseThrow();

    assertTrue(budgetLow.laterality() > budgetHigh.laterality(),
            "Low comprehension should boost laterality more than high: " +
            budgetLow.laterality() + " vs " + budgetHigh.laterality());
}

@Test
void null_channel_preserves_baseline() {
    var participant = withChannelAndConfig(DriveAxis.CURIOSITY, 0.5, null,
            ChannelModulationConfig.defaults());
    participant.tick(context());

    var budget = participant.currentBudget(AGENT, TENANT).orElseThrow();
    var baseline = baselineBudget(DriveAxis.CURIOSITY, 0.5);

    assertEquals(baseline.breadth(), budget.breadth(), 0.001);
    assertEquals(baseline.laterality(), budget.laterality(), 0.001);
    assertEquals(baseline.depth(), budget.depth(), 0.001);
    assertEquals(baseline.convergence(), budget.convergence(), 0.001);
}

@Test
void null_channel_modulation_config_preserves_baseline() {
    var channel = new CuriosityChannel(HedonicType.D_TYPE, 0.5, FocusType.SPECIFIC, 0.9);
    var participant = withChannelAndConfig(DriveAxis.CURIOSITY, 0.5, channel, null);
    participant.tick(context());

    var budget = participant.currentBudget(AGENT, TENANT).orElseThrow();
    var baseline = baselineBudget(DriveAxis.CURIOSITY, 0.5);

    assertEquals(baseline.breadth(), budget.breadth(), 0.001);
    assertEquals(baseline.depth(), budget.depth(), 0.001);
}
```

Add helpers:

```java
private SearchBudgetTickParticipant withChannelAndConfig(
        DriveAxis axis, double intensity,
        @Nullable CuriosityChannel channel,
        @Nullable ChannelModulationConfig channelMod) {
    var curiosityDrive = mock(CuriosityDrive.class);
    when(curiosityDrive.lastChannel()).thenReturn(channel);

    var config = channelMod != null
        ? new SearchBudgetConfig(6.0, 0.5, 6.0, 0.5, 0.7, 3.0, 0.5, 6.0, 0.5, 1.4, channelMod)
        : SearchBudgetConfig.defaults();
    var reg = mock(CognitiveDefaultsRegistry.class);
    when(reg.forAgentOrDefaults(anyString())).thenReturn(
            CognitiveDefaults.empty(AGENT).withSearchBudgetConfig(config));

    return new SearchBudgetTickParticipant(
            mockDriveOrch(axis, intensity), null, null, null, reg, null, curiosityDrive);
}

private SearchBudget baselineBudget(DriveAxis axis, double intensity) {
    var participant = new SearchBudgetTickParticipant(
            mockDriveOrch(axis, intensity), null, null, null, registry(), null, null);
    participant.tick(context());
    return participant.currentBudget(AGENT, TENANT).orElseThrow();
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=SearchBudgetTickParticipantTest#specific_focus_high_gap_boosts_depth_reduces_breadth -DfailIfNoTests=false`
Expected: Compilation error — constructor doesn't accept CuriosityDrive

- [ ] **Step 3: Add CuriosityDrive dependency and channel modulation**

Update SearchBudgetTickParticipant:
1. Add `@Nullable CuriosityDrive curiosityDrive` as 7th constructor parameter
2. Store as field
3. In `tick()`, after computing the 4 base values, apply channel modulation:

```java
private final @Nullable CuriosityDrive curiosityDrive;

// In constructor: add parameter, assign field

// In tick(), after computing breadth/laterality/depth/convergence
// and before creating the SearchBudget:
if (curiosityDrive != null && config.channelModulation() != null) {
    var channel = curiosityDrive.lastChannel();
    if (channel != null) {
        var mod = config.channelModulation();

        // Focus type modulation (discrete)
        if (channel.focus() == FocusType.SPECIFIC) {
            depth += channel.gapProximity() * mod.specificDepthBoost();
            breadth -= channel.gapProximity() * mod.specificBreadthPenalty();
        } else {
            breadth += mod.diversiveBreadthBoost();
            laterality += mod.diversiveLateralityBoost();
            convergence -= mod.diversiveConvergencePenalty();
        }

        // Hedonic type modulation (discrete)
        if (channel.hedonic() == HedonicType.D_TYPE) {
            convergence += mod.deprivationConvergenceBoost();
        } else if (channel.hedonic() == HedonicType.BLENDED) {
            convergence += mod.deprivationConvergenceBoost() * 0.5;
        }

        // Comprehension modulation (continuous gradient)
        laterality += (1.0 - channel.comprehension()) * mod.lowComprehensionLateralityBoost();
        convergence += channel.comprehension() * mod.highComprehensionConvergenceBoost();
    }
}
```

- [ ] **Step 4: Fix existing constructor calls**

Update all `new SearchBudgetTickParticipant(` calls to pass `null` as the 7th arg (CuriosityDrive). There are 4 in test code and 1 in CognitionCore.configureSearchBudget().

- [ ] **Step 5: Run all SearchBudgetTickParticipant tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=SearchBudgetTickParticipantTest`
Expected: ALL PASS

- [ ] **Step 6: Run SearchBudgetPathologicalThresholdTest to verify no regression**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=SearchBudgetPathologicalThresholdTest`
Expected: ALL PASS

- [ ] **Step 7: Commit**

```bash
git add cognition/src/main/java/io/casehub/neocortex/cognition/search/SearchBudgetTickParticipant.java \
       cognition/src/test/java/io/casehub/neocortex/cognition/search/SearchBudgetTickParticipantTest.java
git commit -m "feat(#847): wire CuriosityChannel modulation into SearchBudgetTickParticipant

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Wire CuriosityDrive into CognitionCore.configureSearchBudget()

**Files:**
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCore.java:810-828`

**Interfaces:**
- Consumes: `CuriosityDrive` from caller (same instance passed to `configureCuriosity()`)
- Produces: Updated `configureSearchBudget()` signature with `@Nullable CuriosityDrive` parameter

- [ ] **Step 1: Update configureSearchBudget() signature and wiring**

Add `@Nullable io.casehub.neocortex.cognition.drive.CuriosityDrive curiosityDrive` parameter. Pass it to the SearchBudgetTickParticipant constructor:

```java
public void configureSearchBudget(
        DriveOrchestrator driveOrchestrator,
        @Nullable MoodOrchestrator moodOrchestrator,
        io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry defaultsRegistry,
        io.casehub.neocortex.mindmap.MindMapStore mindMapStore,
        @Nullable io.casehub.neocortex.cognition.drive.CuriosityDrive curiosityDrive) {
    var participant = new io.casehub.neocortex.cognition.search.SearchBudgetTickParticipant(
            driveOrchestrator,
            this.appraisalParticipant,
            this.gutFeelingParticipant,
            moodOrchestrator,
            defaultsRegistry,
            config,
            curiosityDrive);
    addParticipant(CognitionPhase.DERIVED, participant);
    chainSectionCustomizer(sections -> {
        var result = new java.util.ArrayList<>(sections);
        result.add(new io.casehub.neocortex.cognition.search.SearchBudgetPromptSection(
                participant, mindMapStore, this.lastContinuumMap));
        return result;
    });
}
```

- [ ] **Step 2: Build cognition module**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn compile -pl cognition`
Expected: BUILD SUCCESS (no callers within neocortex — external callers in agency need separate update)

- [ ] **Step 3: Commit**

```bash
git add cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCore.java
git commit -m "feat(#847): add CuriosityDrive parameter to CognitionCore.configureSearchBudget()

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Batch 3: CDE derivation and description phase

### Task 5: Add deriveChannelModulationDefaults() to CDE

**Files:**
- Modify: `cognitive-index/src/main/java/io/casehub/neocortex/cognitive/index/CognitiveDerivationEngine.java`
- Modify: `cognitive-index/src/test/java/io/casehub/neocortex/cognitive/index/CognitiveDerivationEngineSearchBudgetTest.java`

**Interfaces:**
- Consumes: PID-5 facets (WITHDRAWAL, INTIMACY_AVOIDANCE, SUSPICIOUSNESS, IMPULSIVITY, ANXIOUSNESS + ruleFollowing axis)
- Produces: `ChannelModulationConfig` derived from personality, set on the returned `SearchBudgetConfig`

- [ ] **Step 1: Write failing tests for channel modulation derivation**

Add tests to CognitiveDerivationEngineSearchBudgetTest:

```java
@Test
void high_openness_boosts_diversive_magnitudes() {
    // High openness = low withdrawal + low intimacy avoidance + low suspiciousness
    var facets = List.of(
            new PersonalityFacet(PersonalityFacet.WITHDRAWAL, 0.1),
            new PersonalityFacet(PersonalityFacet.INTIMACY_AVOIDANCE, 0.1),
            new PersonalityFacet(PersonalityFacet.SUSPICIOUSNESS, 0.1),
            new PersonalityFacet(PersonalityFacet.RISK_TAKING, 0.5),
            new PersonalityFacet(PersonalityFacet.IMPULSIVITY, 0.5));
    var config = CognitiveDerivationEngine.deriveSearchBudgetDefaults(facets, null, 0.5);
    assertNotNull(config);
    assertNotNull(config.channelModulation());

    var defaults = ChannelModulationConfig.defaults();
    assertTrue(config.channelModulation().diversiveBreadthBoost() > defaults.diversiveBreadthBoost(),
            "High openness should boost diversive breadth magnitude");
    assertTrue(config.channelModulation().diversiveLateralityBoost() > defaults.diversiveLateralityBoost(),
            "High openness should boost diversive laterality magnitude");
}

@Test
void high_conscientiousness_boosts_specific_depth_magnitude() {
    var facets = List.of(
            new PersonalityFacet(PersonalityFacet.IMPULSIVITY, 0.1),  // low impulsivity = high conscientiousness
            new PersonalityFacet(PersonalityFacet.WITHDRAWAL, 0.5),
            new PersonalityFacet(PersonalityFacet.INTIMACY_AVOIDANCE, 0.5),
            new PersonalityFacet(PersonalityFacet.SUSPICIOUSNESS, 0.5),
            new PersonalityFacet(PersonalityFacet.RISK_TAKING, 0.5));
    var axes = new DispositionAxes(null, null, null, null, "strict", null, null);
    var config = CognitiveDerivationEngine.deriveSearchBudgetDefaults(facets, axes, 0.5);
    assertNotNull(config);
    assertNotNull(config.channelModulation());

    var defaults = ChannelModulationConfig.defaults();
    assertTrue(config.channelModulation().specificDepthBoost() > defaults.specificDepthBoost(),
            "High conscientiousness should boost specific depth magnitude");
    assertTrue(config.channelModulation().highComprehensionConvergenceBoost() > defaults.highComprehensionConvergenceBoost(),
            "High conscientiousness should boost comprehension convergence magnitude");
}

@Test
void high_anxiousness_boosts_deprivation_convergence() {
    var facets = List.of(
            new PersonalityFacet(PersonalityFacet.ANXIOUSNESS, 0.9),
            new PersonalityFacet(PersonalityFacet.WITHDRAWAL, 0.5),
            new PersonalityFacet(PersonalityFacet.INTIMACY_AVOIDANCE, 0.5),
            new PersonalityFacet(PersonalityFacet.SUSPICIOUSNESS, 0.5),
            new PersonalityFacet(PersonalityFacet.RISK_TAKING, 0.5),
            new PersonalityFacet(PersonalityFacet.IMPULSIVITY, 0.5));
    var config = CognitiveDerivationEngine.deriveSearchBudgetDefaults(facets, null, 0.5);
    assertNotNull(config);
    assertNotNull(config.channelModulation());

    var defaults = ChannelModulationConfig.defaults();
    assertTrue(config.channelModulation().deprivationConvergenceBoost() > defaults.deprivationConvergenceBoost(),
            "High anxiousness should boost deprivation convergence magnitude");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-index -Dtest=CognitiveDerivationEngineSearchBudgetTest#high_openness_boosts_diversive_magnitudes -DfailIfNoTests=false`
Expected: FAIL — channelModulation() returns null (currently passed as null in the constructor)

- [ ] **Step 3: Implement deriveChannelModulationDefaults() and wire into deriveSearchBudgetDefaults()**

Add the private method to CognitiveDerivationEngine:

```java
private static ChannelModulationConfig deriveChannelModulationDefaults(
        List<PersonalityFacet> facets, DispositionAxes axes) {
    double withdrawal = facetScore(facets, PersonalityFacet.WITHDRAWAL, 0.5);
    double intimacyAvoidance = facetScore(facets, PersonalityFacet.INTIMACY_AVOIDANCE, 0.5);
    double suspiciousness = facetScore(facets, PersonalityFacet.SUSPICIOUSNESS, 0.5);
    double impulsivity = facetScore(facets, PersonalityFacet.IMPULSIVITY, 0.5);
    double anxiousness = facetScore(facets, PersonalityFacet.ANXIOUSNESS, 0.5);

    double ruleFollowingNumeric = axes != null
            ? RULE_FOLLOWING_NUMERIC.getOrDefault(lower(axes.ruleFollowing()), 0.5) : 0.5;

    double openness = (1.0 - withdrawal + 1.0 - intimacyAvoidance + 1.0 - suspiciousness) / 3.0;
    double conscientiousness = (ruleFollowingNumeric + 1.0 - impulsivity) / 2.0;

    var d = ChannelModulationConfig.defaults();
    return new ChannelModulationConfig(
            d.specificDepthBoost() + (conscientiousness - 0.5) * 0.08,
            d.specificBreadthPenalty() + (conscientiousness - 0.5) * 0.06,
            d.diversiveBreadthBoost() + (openness - 0.5) * 0.10,
            d.diversiveLateralityBoost() + (openness - 0.5) * 0.08,
            d.diversiveConvergencePenalty() + (openness - 0.5) * 0.06,
            d.deprivationConvergenceBoost() + (anxiousness - 0.5) * 0.08,
            d.lowComprehensionLateralityBoost() + (openness - 0.5) * 0.08,
            d.highComprehensionConvergenceBoost() + (conscientiousness - 0.5) * 0.08);
}
```

Then update `deriveSearchBudgetDefaults()` to call it and pass the result to the SearchBudgetConfig constructor:

```java
// At the end of deriveSearchBudgetDefaults(), replace the return:
var channelModulation = deriveChannelModulationDefaults(facets, axes);

return new SearchBudgetConfig(
        Math.max(1.0, breadthSteepness),
        clamp01(breadthMidpoint),
        defaults.lateralitySteepness(),
        clamp01(lateralityMidpoint),
        defaults.lateralityThreatFloor(),
        defaults.depthSteepness(),
        clamp01(depthMidpoint),
        defaults.convergenceSteepness(),
        clamp01(convergenceMidpoint),
        pathologicalThreshold,
        channelModulation);
```

- [ ] **Step 4: Run all CDE search budget tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-index -Dtest=CognitiveDerivationEngineSearchBudgetTest`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add cognitive-index/src/main/java/io/casehub/neocortex/cognitive/index/CognitiveDerivationEngine.java \
       cognitive-index/src/test/java/io/casehub/neocortex/cognitive/index/CognitiveDerivationEngineSearchBudgetTest.java
git commit -m "feat(#847): derive ChannelModulationConfig from personality in CDE

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Update SearchBudgetDescriptionGenerationPhase

**Files:**
- Modify: `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/SearchBudgetDescriptionGenerationPhase.java:48-60,162-175`
- Modify: `mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/SearchBudgetDescriptionGenerationPhaseTest.java`

**Interfaces:**
- Consumes: `SearchBudgetConfig.channelModulation()` → `@Nullable ChannelModulationConfig`
- Produces: Updated LLM prompt and fallback prose that include channel tendency descriptions

- [ ] **Step 1: Write failing test for channel tendency in description**

Add a test to SearchBudgetDescriptionGenerationPhaseTest:

```java
@Test
void fallback_includes_channel_tendencies_when_present() {
    var channelMod = new ChannelModulationConfig(
            0.18, 0.12, 0.08, 0.06, 0.04, 0.16, 0.06, 0.14);
    var config = new SearchBudgetConfig(6.0, 0.5, 6.0, 0.5, 0.7, 3.0, 0.5, 6.0, 0.5, 1.4, channelMod);
    String fallback = SearchBudgetDescriptionGenerationPhase.generateFallback(config);

    assertTrue(fallback.contains("narrows") || fallback.contains("specific"),
            "High specificDepthBoost should produce deep-diver description: " + fallback);
}

@Test
void fallback_omits_channel_tendencies_when_null() {
    var config = SearchBudgetConfig.defaults();
    String fallback = SearchBudgetDescriptionGenerationPhase.generateFallback(config);
    assertNotNull(fallback);
    assertFalse(fallback.isEmpty());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest=SearchBudgetDescriptionGenerationPhaseTest#fallback_includes_channel_tendencies_when_present -DfailIfNoTests=false`
Expected: FAIL — fallback doesn't mention channel tendencies

- [ ] **Step 3: Update generateFallback() and buildUserPrompt()**

Update `generateFallback()` to include channel tendency prose when `config.channelModulation()` is non-null:

```java
static String generateFallback(SearchBudgetConfig config) {
    // ... existing logic ...

    if (config.channelModulation() != null) {
        var mod = config.channelModulation();
        var d = ChannelModulationConfig.defaults();
        if (mod.specificDepthBoost() > d.specificDepthBoost() + 0.02) {
            sb.append(" When a specific question grips you, the world narrows and depth pulls you in.");
        }
        if (mod.diversiveBreadthBoost() > d.diversiveBreadthBoost() + 0.02) {
            sb.append(" Broad curiosity fans out quickly — angles multiply before convergence arrives.");
        }
        if (mod.deprivationConvergenceBoost() > d.deprivationConvergenceBoost() + 0.02) {
            sb.append(" Unresolved gaps create pressure — you feel the pull to close them.");
        }
    }

    return sb.toString().trim();
}
```

Update `buildUserPrompt()` to include channel modulation magnitudes when present:

```java
private String buildUserPrompt(String agentId, SearchBudgetConfig config) {
    var sb = new StringBuilder();
    sb.append(String.format(
            "Agent: %s\nBreadth steepness: %.1f, midpoint: %.2f\n"
                    + "Laterality steepness: %.1f, midpoint: %.2f, threat floor: %.2f\n"
                    + "Depth steepness: %.1f, midpoint: %.2f\n"
                    + "Convergence steepness: %.1f, midpoint: %.2f\n"
                    + "Pathological threshold: %.2f",
            agentId,
            config.breadthSteepness(), config.breadthMidpoint(),
            config.lateralitySteepness(), config.lateralityMidpoint(), config.lateralityThreatFloor(),
            config.depthSteepness(), config.depthMidpoint(),
            config.convergenceSteepness(), config.convergenceMidpoint(),
            config.pathologicalThreshold()));

    if (config.channelModulation() != null) {
        var mod = config.channelModulation();
        sb.append(String.format(
                "\nChannel tendencies:\n"
                        + "- Specific focus depth boost: %.2f\n"
                        + "- Diversive breadth boost: %.2f\n"
                        + "- Deprivation convergence boost: %.2f\n"
                        + "- Low comprehension laterality boost: %.2f\n"
                        + "- High comprehension convergence boost: %.2f",
                mod.specificDepthBoost(), mod.diversiveBreadthBoost(),
                mod.deprivationConvergenceBoost(),
                mod.lowComprehensionLateralityBoost(),
                mod.highComprehensionConvergenceBoost()));
    }

    return sb.toString();
}
```

- [ ] **Step 4: Run all SearchBudgetDescriptionGenerationPhase tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest=SearchBudgetDescriptionGenerationPhaseTest`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/SearchBudgetDescriptionGenerationPhase.java \
       mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/SearchBudgetDescriptionGenerationPhaseTest.java
git commit -m "feat(#847): include channel tendencies in SearchBudgetDescriptionGenerationPhase

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Full build verification and CLAUDE.md update

**Files:**
- Modify: `CLAUDE.md` (update SearchBudgetConfig field count and add ChannelModulationConfig)

**Interfaces:**
- Consumes: All previous tasks

- [ ] **Step 1: Run full build with tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install`
Expected: BUILD SUCCESS

- [ ] **Step 2: Update CLAUDE.md**

Update the SearchBudgetConfig description to mention the channelModulation field. Add ChannelModulationConfig to the cognitive-api module description.

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs(#847): update CLAUDE.md with ChannelModulationConfig

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## References

- [2026-11-08-curiosity-search-budget-coupling-design.md] — design spec this plan implements
- `cognition/src/main/java/io/casehub/neocortex/cognition/search/SearchBudgetTickParticipant.java` — primary modification target
- `cognition/src/main/java/io/casehub/neocortex/cognition/drive/CuriosityDrive.java` — channel state caching
- `cognitive-api/src/main/java/io/casehub/neocortex/cognitive/SearchBudgetConfig.java` — config extension
- `cognitive-index/src/main/java/io/casehub/neocortex/cognitive/index/CognitiveDerivationEngine.java:645-688` — CDE derivation
- `cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCore.java:810-828` — wiring
- `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/SearchBudgetDescriptionGenerationPhase.java` — description phase
- casehubio/neocortex#847 — focal issue
- casehubio/neocortex#846 — parent epic
