# ReflectionSynthesizer LLM Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** neocortex#338 — feat: implement ReflectionSynthesizer — LLM-backed heuristic extraction from experience trajectories
**Issue group:** neocortex#338, blocks#320

**Goal:** Implement cross-repo reflection synthesis: pure Java trajectory grouping in neocortex memory-core, LLM-backed heuristic extraction in blocks blocks-core.

**Architecture:** TrajectoryGrouper (neocortex memory-core) groups flat experience memories into structured trajectories by caseId, orders by timestamp, classifies FAILURE/SUCCESS/NEUTRAL via outcome-status attribute. LlmReflectionSynthesizer (blocks blocks-core) uses TrajectoryGrouper for pre-processing, builds an LLM prompt, calls AgentProvider, parses JSON response into ReflectionEvent records with source traceability.

**Tech Stack:** Java 21, Quarkus CDI, AgentProvider (casehub-platform-agent-api), Jackson for JSON parsing

## Global Constraints

- Java 21 source level, Java 26 JVM
- No new module — TrajectoryGrouper goes in existing `memory-core`, LlmReflectionSynthesizer goes in existing `blocks-core`
- TrajectoryGrouper: pure Java, zero new dependencies
- LlmReflectionSynthesizer: `@Alternative @Priority(1)`, `Instance<AgentProvider>` for graceful degradation
- All commits reference an issue: `Refs #338` for neocortex, `Refs #320` for blocks
- TDD: failing test first, then implementation
- IntelliJ MCP for all code navigation and structural editing

---

## Batch 1: Trajectory Infrastructure (neocortex)

### Task 1: TrajectoryGrouper — data types and grouping logic

**Files:**
- Create: `memory-core/src/main/java/io/casehub/neocortex/memory/reflection/runtime/Trajectory.java`
- Create: `memory-core/src/main/java/io/casehub/neocortex/memory/reflection/runtime/TrajectoryStep.java`
- Create: `memory-core/src/main/java/io/casehub/neocortex/memory/reflection/runtime/TrajectoryOutcome.java`
- Create: `memory-core/src/main/java/io/casehub/neocortex/memory/reflection/runtime/TrajectoryGrouper.java`
- Create: `memory-core/src/test/java/io/casehub/neocortex/memory/reflection/runtime/TrajectoryGrouperTest.java`

**Interfaces:**
- Consumes: `Memory` (memory-api), `ExperienceAttributeKeys` (memory-api)
- Produces: `TrajectoryGrouper.group(List<Memory>) → List<Trajectory>`, `Trajectory(caseId, steps, outcome)`, `TrajectoryStep(turnId, events, timestamp)`, `TrajectoryOutcome` enum

- [ ] **Step 1: Write TrajectoryOutcome enum**

```java
package io.casehub.neocortex.memory.reflection.runtime;

public enum TrajectoryOutcome { FAILURE, SUCCESS, NEUTRAL }
```

- [ ] **Step 2: Write TrajectoryStep record**

```java
package io.casehub.neocortex.memory.reflection.runtime;

import io.casehub.neocortex.memory.Memory;
import java.time.Instant;
import java.util.List;

public record TrajectoryStep(String turnId, List<Memory> events, Instant timestamp) {
    public TrajectoryStep {
        events = List.copyOf(events);
    }
}
```

- [ ] **Step 3: Write Trajectory record**

```java
package io.casehub.neocortex.memory.reflection.runtime;

import java.util.List;

public record Trajectory(String caseId, List<TrajectoryStep> steps, TrajectoryOutcome outcome) {
    public Trajectory {
        steps = List.copyOf(steps);
    }
}
```

- [ ] **Step 4: Write failing tests for TrajectoryGrouper**

```java
package io.casehub.neocortex.memory.reflection.runtime;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TrajectoryGrouperTest {

    private static final MemoryDomain EXPERIENCE = new MemoryDomain("experience");

    private Memory mem(String memoryId, String caseId, String turnId, String eventType,
                       Instant createdAt, Map<String, String> extraAttrs) {
        var attrs = new java.util.HashMap<>(Map.of(
            ExperienceAttributeKeys.EVENT_TYPE, eventType,
            ExperienceAttributeKeys.TIMESTAMP, createdAt.toString()));
        if (turnId != null) attrs.put(ExperienceAttributeKeys.TURN_ID, turnId);
        attrs.putAll(extraAttrs);
        return new Memory(memoryId, Subject.of("agent", "a1"), EXPERIENCE, "t1",
            caseId, "description", attrs, createdAt, null, null, null, null, null, null);
    }

    private Memory mem(String memoryId, String caseId, String turnId, String eventType,
                       Instant createdAt) {
        return mem(memoryId, caseId, turnId, eventType, createdAt, Map.of());
    }

    @Test
    void groupsByCaseId() {
        var now = Instant.now();
        var memories = List.of(
            mem("m1", "case-A", "t1", "observation", now),
            mem("m2", "case-B", "t1", "action", now.plusSeconds(1)),
            mem("m3", "case-A", "t2", "outcome", now.plusSeconds(2))
        );
        var trajectories = TrajectoryGrouper.group(memories);
        assertThat(trajectories).hasSize(2);
        assertThat(trajectories).extracting(Trajectory::caseId)
            .containsExactlyInAnyOrder("case-A", "case-B");
    }

    @Test
    void ordersStepsByTimestamp() {
        var now = Instant.now();
        var memories = List.of(
            mem("m1", "case-A", "t2", "action", now.plusSeconds(10)),
            mem("m2", "case-A", "t1", "observation", now)
        );
        var trajectories = TrajectoryGrouper.group(memories);
        assertThat(trajectories).hasSize(1);
        var steps = trajectories.get(0).steps();
        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).turnId()).isEqualTo("t1");
        assertThat(steps.get(1).turnId()).isEqualTo("t2");
    }

    @Test
    void classifiesFailureViaOutcomeStatus() {
        var now = Instant.now();
        var memories = List.of(
            mem("m1", "case-A", "t1", "observation", now),
            mem("m2", "case-A", "t2", "outcome", now.plusSeconds(1),
                Map.of(ExperienceAttributeKeys.OUTCOME_STATUS, "failed",
                       ExperienceAttributeKeys.RESULT, "timeout"))
        );
        var trajectories = TrajectoryGrouper.group(memories);
        assertThat(trajectories.get(0).outcome()).isEqualTo(TrajectoryOutcome.FAILURE);
    }

    @Test
    void classifiesSuccessViaOutcomeStatus() {
        var now = Instant.now();
        var memories = List.of(
            mem("m1", "case-A", "t1", "outcome", now,
                Map.of(ExperienceAttributeKeys.OUTCOME_STATUS, "success",
                       ExperienceAttributeKeys.RESULT, "completed"))
        );
        var trajectories = TrajectoryGrouper.group(memories);
        assertThat(trajectories.get(0).outcome()).isEqualTo(TrajectoryOutcome.SUCCESS);
    }

    @Test
    void classifiesNeutralWhenNoOutcomeStatus() {
        var now = Instant.now();
        var memories = List.of(
            mem("m1", "case-A", "t1", "outcome", now,
                Map.of(ExperienceAttributeKeys.RESULT, "completed"))
        );
        var trajectories = TrajectoryGrouper.group(memories);
        assertThat(trajectories.get(0).outcome()).isEqualTo(TrajectoryOutcome.NEUTRAL);
    }

    @Test
    void classifiesNeutralWhenNoOutcomeEvents() {
        var now = Instant.now();
        var memories = List.of(
            mem("m1", "case-A", "t1", "observation", now),
            mem("m2", "case-A", "t2", "action", now.plusSeconds(1))
        );
        var trajectories = TrajectoryGrouper.group(memories);
        assertThat(trajectories.get(0).outcome()).isEqualTo(TrajectoryOutcome.NEUTRAL);
    }

    @Test
    void sortsFailureFirst() {
        var now = Instant.now();
        var memories = List.of(
            mem("m1", "case-S", "t1", "outcome", now,
                Map.of(ExperienceAttributeKeys.OUTCOME_STATUS, "success",
                       ExperienceAttributeKeys.RESULT, "ok")),
            mem("m2", "case-F", "t1", "outcome", now,
                Map.of(ExperienceAttributeKeys.OUTCOME_STATUS, "failed",
                       ExperienceAttributeKeys.RESULT, "err"))
        );
        var trajectories = TrajectoryGrouper.group(memories);
        assertThat(trajectories.get(0).outcome()).isEqualTo(TrajectoryOutcome.FAILURE);
        assertThat(trajectories.get(1).outcome()).isEqualTo(TrajectoryOutcome.SUCCESS);
    }

    @Test
    void skipsNullCaseId() {
        var now = Instant.now();
        var memories = List.of(
            mem("m1", null, "t1", "observation", now),
            mem("m2", "case-A", "t1", "action", now)
        );
        var trajectories = TrajectoryGrouper.group(memories);
        assertThat(trajectories).hasSize(1);
        assertThat(trajectories.get(0).caseId()).isEqualTo("case-A");
    }

    @Test
    void handlesNullTurnId() {
        var now = Instant.now();
        var memories = List.of(
            mem("m1", "case-A", null, "observation", now)
        );
        var trajectories = TrajectoryGrouper.group(memories);
        assertThat(trajectories).hasSize(1);
        assertThat(trajectories.get(0).steps().get(0).turnId()).isNull();
    }

    @Test
    void emptyInputReturnsEmptyList() {
        assertThat(TrajectoryGrouper.group(List.of())).isEmpty();
    }

    @Test
    void groupsMultipleEventsInSameTurn() {
        var now = Instant.now();
        var memories = List.of(
            mem("m1", "case-A", "t1", "observation", now),
            mem("m2", "case-A", "t1", "action", now.plusSeconds(1))
        );
        var trajectories = TrajectoryGrouper.group(memories);
        assertThat(trajectories.get(0).steps()).hasSize(1);
        assertThat(trajectories.get(0).steps().get(0).events()).hasSize(2);
    }
}
```

- [ ] **Step 5: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl memory-core -Dtest=TrajectoryGrouperTest -f /Users/mdproctor/claude/casehub/slots/203/neocortex/pom.xml`
Expected: compilation failure (TrajectoryGrouper class not found)

- [ ] **Step 6: Implement TrajectoryGrouper**

```java
package io.casehub.neocortex.memory.reflection.runtime;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class TrajectoryGrouper {

    private static final Set<String> FAILURE_STATUSES = Set.of(
        "failed", "failure", "error", "rejected", "timeout");
    private static final Set<String> SUCCESS_STATUSES = Set.of(
        "success", "successful", "completed", "passed", "resolved");

    private TrajectoryGrouper() {}

    public static List<Trajectory> group(List<Memory> sources) {
        if (sources.isEmpty()) return List.of();

        Map<String, List<Memory>> byCase = sources.stream()
            .filter(m -> m.caseId() != null)
            .collect(Collectors.groupingBy(Memory::caseId, LinkedHashMap::new,
                Collectors.toList()));

        List<Trajectory> trajectories = new ArrayList<>();
        for (var entry : byCase.entrySet()) {
            var caseMemories = entry.getValue().stream()
                .sorted(Comparator.comparing(Memory::createdAt))
                .toList();

            var steps = buildSteps(caseMemories);
            var outcome = classifyOutcome(caseMemories);
            trajectories.add(new Trajectory(entry.getKey(), steps, outcome));
        }

        trajectories.sort(Comparator.comparingInt(t -> t.outcome().ordinal()));
        return trajectories;
    }

    private static List<TrajectoryStep> buildSteps(List<Memory> sorted) {
        Map<String, List<Memory>> byTurn = new LinkedHashMap<>();
        for (var mem : sorted) {
            String turnId = mem.attributes().get(ExperienceAttributeKeys.TURN_ID);
            byTurn.computeIfAbsent(turnId, k -> new ArrayList<>()).add(mem);
        }

        List<TrajectoryStep> steps = new ArrayList<>();
        for (var turnEntry : byTurn.entrySet()) {
            var events = turnEntry.getValue();
            Instant earliest = events.stream()
                .map(Memory::createdAt)
                .min(Comparator.naturalOrder())
                .orElse(Instant.EPOCH);
            steps.add(new TrajectoryStep(turnEntry.getKey(), events, earliest));
        }
        steps.sort(Comparator.comparing(TrajectoryStep::timestamp));
        return steps;
    }

    private static TrajectoryOutcome classifyOutcome(List<Memory> memories) {
        boolean hasOutcome = false;
        boolean hasFailure = false;
        boolean hasSuccess = false;

        for (var mem : memories) {
            String eventType = mem.attributes().get(ExperienceAttributeKeys.EVENT_TYPE);
            if (!"outcome".equals(eventType)) continue;

            hasOutcome = true;
            String status = mem.attributes().get(ExperienceAttributeKeys.OUTCOME_STATUS);
            if (status == null) continue;

            String lower = status.toLowerCase();
            if (FAILURE_STATUSES.contains(lower)) hasFailure = true;
            else if (SUCCESS_STATUSES.contains(lower)) hasSuccess = true;
        }

        if (hasFailure) return TrajectoryOutcome.FAILURE;
        if (!hasOutcome) return TrajectoryOutcome.NEUTRAL;
        if (hasSuccess) return TrajectoryOutcome.SUCCESS;
        return TrajectoryOutcome.NEUTRAL;
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl memory-core -Dtest=TrajectoryGrouperTest -f /Users/mdproctor/claude/casehub/slots/203/neocortex/pom.xml`
Expected: all 10 tests PASS

- [ ] **Step 8: Commit**

```bash
git add memory-core/src/main/java/io/casehub/neocortex/memory/reflection/runtime/TrajectoryOutcome.java \
       memory-core/src/main/java/io/casehub/neocortex/memory/reflection/runtime/TrajectoryStep.java \
       memory-core/src/main/java/io/casehub/neocortex/memory/reflection/runtime/Trajectory.java \
       memory-core/src/main/java/io/casehub/neocortex/memory/reflection/runtime/TrajectoryGrouper.java \
       memory-core/src/test/java/io/casehub/neocortex/memory/reflection/runtime/TrajectoryGrouperTest.java
git commit -m "feat(#338): TrajectoryGrouper — group experience memories into structured trajectories

Refs #338"
```

---

## Batch 2: LLM Reflection Synthesizer (blocks)

### Task 2: LlmReflectionSynthesizer — prompt building, LLM call, response parsing

**Files:**
- Create: `blocks-core/src/main/java/io/casehub/blocks/agentic/social/reflection/LlmReflectionSynthesizer.java`
- Modify: `blocks-core/pom.xml` — add `casehub-neocortex-memory-core` dependency
- Create: `blocks-core/src/test/java/io/casehub/blocks/agentic/social/reflection/LlmReflectionSynthesizerTest.java`

**Interfaces:**
- Consumes: `ReflectionSynthesizer` SPI (memory-api), `TrajectoryGrouper` (memory-core), `AgentProvider` (platform-agent-api), `Memory` (memory-api), `CaseMemoryStore` (memory-api)
- Produces: `LlmReflectionSynthesizer` — `@Alternative @Priority(1)` implementing `ReflectionSynthesizer`

- [ ] **Step 1: Add memory-core dependency to blocks-core pom.xml**

Add to `blocks-core/pom.xml` dependencies section:
```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-neocortex-memory-core</artifactId>
</dependency>
```

- [ ] **Step 2: Write failing tests for LlmReflectionSynthesizer**

```java
package io.casehub.blocks.agentic.social.reflection;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;
import io.casehub.neocortex.memory.reflection.ReflectionEvent;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmReflectionSynthesizerTest {

    private static final MemoryDomain EXPERIENCE = new MemoryDomain("experience");

    private Memory mem(String memoryId, String caseId, String turnId, String eventType,
                       Instant createdAt, String description, Map<String, String> extraAttrs) {
        var attrs = new java.util.HashMap<>(Map.of(
            ExperienceAttributeKeys.EVENT_TYPE, eventType,
            ExperienceAttributeKeys.TIMESTAMP, createdAt.toString()));
        if (turnId != null) attrs.put(ExperienceAttributeKeys.TURN_ID, turnId);
        attrs.putAll(extraAttrs);
        return new Memory(memoryId, Subject.of("agent", "agent-1"), EXPERIENCE, "tenant-1",
            caseId, description, attrs, createdAt, null, null, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private Instance<AgentProvider> mockAgentProviderInstance(AgentProvider provider) {
        Instance<AgentProvider> instance = mock(Instance.class);
        when(instance.isResolvable()).thenReturn(provider != null);
        if (provider != null) when(instance.get()).thenReturn(provider);
        return instance;
    }

    private AgentProvider mockAgentProvider(String jsonResponse) {
        AgentProvider provider = mock(AgentProvider.class);
        AgentEvent event = mock(AgentEvent.class);
        when(event.text()).thenReturn(jsonResponse);
        when(provider.chat(any(AgentSessionConfig.class), anyString())).thenReturn(event);
        return provider;
    }

    @Test
    void returnsEmptyWhenAgentProviderUnavailable() {
        var synth = new LlmReflectionSynthesizer(mockAgentProviderInstance(null), null);
        var result = synth.synthesize("agent-1", "tenant-1", List.of(), 1);
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoSources() {
        var provider = mockAgentProvider("{\"heuristics\": []}");
        var synth = new LlmReflectionSynthesizer(mockAgentProviderInstance(provider), null);
        var result = synth.synthesize("agent-1", "tenant-1", List.of(), 1);
        assertThat(result).isEmpty();
    }

    @Test
    void extractsHeuristicsFromValidResponse() {
        var now = Instant.now();
        var sources = List.of(
            mem("m1", "case-1", "t1", "observation", now, "User asked about billing", Map.of()),
            mem("m2", "case-1", "t2", "action", now.plusSeconds(1), "Looked up account", Map.of()),
            mem("m3", "case-1", "t3", "outcome", now.plusSeconds(2), "Resolved issue",
                Map.of(ExperienceAttributeKeys.OUTCOME_STATUS, "success",
                       ExperienceAttributeKeys.RESULT, "resolved"))
        );

        String json = """
            {"heuristics": [{
                "condition": "user asks about billing",
                "action": "look up account first before asking clarifying questions",
                "source_cases": ["case-1"],
                "source_turns": ["t1", "t2"]
            }]}""";

        var provider = mockAgentProvider(json);
        var synth = new LlmReflectionSynthesizer(mockAgentProviderInstance(provider), null);
        var result = synth.synthesize("agent-1", "tenant-1", sources, 1);

        assertThat(result).hasSize(1);
        ReflectionEvent event = result.get(0);
        assertThat(event.insight()).isEqualTo(
            "When user asks about billing, look up account first before asking clarifying questions");
        assertThat(event.agentId()).isEqualTo("agent-1");
        assertThat(event.tenantId()).isEqualTo("tenant-1");
        assertThat(event.level()).isEqualTo(1);
        assertThat(event.sourceMemoryIds()).containsExactlyInAnyOrder("m1", "m2");
    }

    @Test
    void handlesMultipleHeuristics() {
        var now = Instant.now();
        var sources = List.of(
            mem("m1", "case-1", "t1", "observation", now, "Observed X", Map.of()),
            mem("m2", "case-1", "t2", "outcome", now.plusSeconds(1), "Failed",
                Map.of(ExperienceAttributeKeys.OUTCOME_STATUS, "failed",
                       ExperienceAttributeKeys.RESULT, "error"))
        );

        String json = """
            {"heuristics": [
                {"condition": "X occurs", "action": "avoid Y",
                 "source_cases": ["case-1"], "source_turns": ["t1"]},
                {"condition": "after failure", "action": "try Z",
                 "source_cases": ["case-1"], "source_turns": ["t2"]}
            ]}""";

        var provider = mockAgentProvider(json);
        var synth = new LlmReflectionSynthesizer(mockAgentProviderInstance(provider), null);
        var result = synth.synthesize("agent-1", "tenant-1", sources, 1);

        assertThat(result).hasSize(2);
    }

    @Test
    void handlesMalformedJson() {
        var now = Instant.now();
        var sources = List.of(
            mem("m1", "case-1", "t1", "observation", now, "Observed X", Map.of())
        );

        var provider = mockAgentProvider("not valid json at all");
        var synth = new LlmReflectionSynthesizer(mockAgentProviderInstance(provider), null);
        var result = synth.synthesize("agent-1", "tenant-1", sources, 1);

        assertThat(result).isEmpty();
    }

    @Test
    void skipsHeuristicsWithInvalidSourceTurns() {
        var now = Instant.now();
        var sources = List.of(
            mem("m1", "case-1", "t1", "observation", now, "Observed X", Map.of())
        );

        String json = """
            {"heuristics": [{
                "condition": "X occurs", "action": "avoid Y",
                "source_cases": ["case-1"], "source_turns": ["nonexistent-turn"]
            }]}""";

        var provider = mockAgentProvider(json);
        var synth = new LlmReflectionSynthesizer(mockAgentProviderInstance(provider), null);
        var result = synth.synthesize("agent-1", "tenant-1", sources, 1);

        assertThat(result).isEmpty();
    }

    @Test
    void tagsDerivationMetadata() {
        var now = Instant.now();
        var sources = List.of(
            mem("m1", "case-1", "t1", "outcome", now, "Failed",
                Map.of(ExperienceAttributeKeys.OUTCOME_STATUS, "failed",
                       ExperienceAttributeKeys.RESULT, "error"))
        );

        String json = """
            {"heuristics": [{
                "condition": "error occurs", "action": "retry with backoff",
                "source_cases": ["case-1"], "source_turns": ["t1"]
            }]}""";

        var provider = mockAgentProvider(json);
        var synth = new LlmReflectionSynthesizer(mockAgentProviderInstance(provider), null);
        var result = synth.synthesize("agent-1", "tenant-1", sources, 1);

        assertThat(result.get(0).metadata()).containsEntry("derivation", "failure");
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl blocks-core -Dtest=LlmReflectionSynthesizerTest -f /Users/mdproctor/claude/casehub/slots/203/blocks/pom.xml`
Expected: compilation failure (LlmReflectionSynthesizer not found)

- [ ] **Step 4: Implement LlmReflectionSynthesizer**

```java
package io.casehub.blocks.agentic.social.reflection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.experience.ExperienceAttributeKeys;
import io.casehub.neocortex.memory.reflection.ReflectionEvent;
import io.casehub.neocortex.memory.reflection.ReflectionQuery;
import io.casehub.neocortex.memory.reflection.ReflectionSynthesizer;
import io.casehub.neocortex.memory.reflection.runtime.Trajectory;
import io.casehub.neocortex.memory.reflection.runtime.TrajectoryGrouper;
import io.casehub.neocortex.memory.reflection.runtime.TrajectoryOutcome;
import io.casehub.neocortex.memory.reflection.runtime.TrajectoryStep;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import jakarta.alternative.Alternative;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Alternative
@Priority(1)
@ApplicationScoped
public class LlmReflectionSynthesizer implements ReflectionSynthesizer {

    private static final Logger LOG = Logger.getLogger(LlmReflectionSynthesizer.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_EXISTING_REFLECTIONS = 20;

    private static final String SYSTEM_PROMPT = """
        You are a reflection agent. Analyse experience trajectories and extract \
        actionable heuristics as conditional rules.

        Respond with JSON:
        {
          "heuristics": [
            {
              "condition": "description of the situation/trigger",
              "action": "what to do or avoid",
              "source_cases": ["case-id-1"],
              "source_turns": ["turn-id-1", "turn-id-2"]
            }
          ]
        }

        Rules:
        - Extract 1-5 heuristics (fewer is better than vague)
        - Each heuristic must be a conditional rule: "when X, do/avoid Y"
        - Prioritise failure-derived heuristics — what went wrong and how to avoid it
        - Each heuristic must reference at least one source case and turn
        - Do not extract heuristics that are obvious or trivially true
        - Do not repeat existing insights listed at the end of the input
        - Respond with valid JSON only""";

    private final Instance<AgentProvider> agentProviderInstance;
    private final Instance<CaseMemoryStore> storeInstance;

    @Inject
    public LlmReflectionSynthesizer(Instance<AgentProvider> agentProviderInstance,
                                     Instance<CaseMemoryStore> storeInstance) {
        this.agentProviderInstance = agentProviderInstance;
        this.storeInstance = storeInstance;
    }

    @Override
    public List<ReflectionEvent> synthesize(String agentId, String tenantId,
                                             List<Memory> sources, int targetLevel) {
        if (!agentProviderInstance.isResolvable()) return List.of();
        if (sources.isEmpty()) return List.of();

        var trajectories = TrajectoryGrouper.group(sources);
        if (trajectories.isEmpty()) return List.of();

        var turnIndex = buildTurnIndex(sources);
        var trajectoryOutcomeIndex = buildTrajectoryOutcomeIndex(trajectories);
        var existingReflections = loadExistingReflections(agentId, tenantId);

        String userPrompt = buildUserPrompt(trajectories, existingReflections);

        try {
            var config = AgentSessionConfig.simple(SYSTEM_PROMPT);
            var response = agentProviderInstance.get().chat(config, userPrompt);
            return parseResponse(response.text(), agentId, tenantId, targetLevel,
                turnIndex, trajectoryOutcomeIndex);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM reflection synthesis failed", e);
            return List.of();
        }
    }

    private Map<String, Set<String>> buildTurnIndex(List<Memory> sources) {
        Map<String, Set<String>> index = new HashMap<>();
        for (var mem : sources) {
            String turnId = mem.attributes().get(ExperienceAttributeKeys.TURN_ID);
            if (turnId != null) {
                index.computeIfAbsent(turnId, k -> new HashSet<>()).add(mem.memoryId());
            }
        }
        return index;
    }

    private Map<String, TrajectoryOutcome> buildTrajectoryOutcomeIndex(List<Trajectory> trajectories) {
        Map<String, TrajectoryOutcome> index = new HashMap<>();
        for (var t : trajectories) {
            index.put(t.caseId(), t.outcome());
        }
        return index;
    }

    private List<String> loadExistingReflections(String agentId, String tenantId) {
        if (storeInstance == null || !storeInstance.isResolvable()) return List.of();
        try {
            var query = ReflectionQuery.forAgent(agentId, tenantId)
                .withLimit(MAX_EXISTING_REFLECTIONS);
            return storeInstance.get().query(query).stream()
                .map(Memory::text)
                .toList();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not load existing reflections for dedup", e);
            return List.of();
        }
    }

    private String buildUserPrompt(List<Trajectory> trajectories,
                                    List<String> existingReflections) {
        var sb = new StringBuilder();
        sb.append("## Experience Trajectories\n\n");

        for (int i = 0; i < trajectories.size(); i++) {
            var t = trajectories.get(i);
            sb.append("### Trajectory ").append(i + 1)
              .append(" [case: ").append(t.caseId())
              .append(", classification: ").append(t.outcome().name())
              .append("]\n\n");

            for (var step : t.steps()) {
                for (var mem : step.events()) {
                    String eventType = mem.attributes().get(ExperienceAttributeKeys.EVENT_TYPE);
                    sb.append("- [").append(eventType != null ? eventType : "unknown")
                      .append("]");
                    if (step.turnId() != null) sb.append(" (turn: ").append(step.turnId()).append(")");
                    sb.append(" ").append(mem.text());

                    String status = mem.attributes().get(ExperienceAttributeKeys.OUTCOME_STATUS);
                    if (status != null) sb.append(" [status: ").append(status).append("]");

                    String result = mem.attributes().get(ExperienceAttributeKeys.RESULT);
                    if (result != null) sb.append(" [result: ").append(result).append("]");

                    sb.append("\n");
                }
            }
            sb.append("\n");
        }

        if (!existingReflections.isEmpty()) {
            sb.append("## Already Known Insights — Do Not Repeat\n\n");
            for (var insight : existingReflections) {
                sb.append("- ").append(insight).append("\n");
            }
        }

        return sb.toString();
    }

    List<ReflectionEvent> parseResponse(String json, String agentId, String tenantId,
                                         int targetLevel, Map<String, Set<String>> turnIndex,
                                         Map<String, TrajectoryOutcome> trajectoryOutcomeIndex) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            LOG.warning("Malformed JSON from LLM reflection synthesis: " + e.getMessage());
            return List.of();
        }

        JsonNode heuristics = root.get("heuristics");
        if (heuristics == null || !heuristics.isArray()) return List.of();

        List<ReflectionEvent> events = new ArrayList<>();
        for (var node : heuristics) {
            var event = parseHeuristic(node, agentId, tenantId, targetLevel,
                turnIndex, trajectoryOutcomeIndex);
            if (event != null) events.add(event);
        }
        return events;
    }

    private ReflectionEvent parseHeuristic(JsonNode node, String agentId, String tenantId,
                                            int targetLevel, Map<String, Set<String>> turnIndex,
                                            Map<String, TrajectoryOutcome> trajectoryOutcomeIndex) {
        JsonNode conditionNode = node.get("condition");
        JsonNode actionNode = node.get("action");
        if (conditionNode == null || actionNode == null) return null;

        String condition = conditionNode.asText();
        String action = actionNode.asText();
        if (condition.isBlank() || action.isBlank()) return null;

        Set<String> sourceMemoryIds = new HashSet<>();
        JsonNode sourceTurns = node.get("source_turns");
        if (sourceTurns != null && sourceTurns.isArray()) {
            for (var turnNode : sourceTurns) {
                String turnId = turnNode.asText();
                Set<String> memIds = turnIndex.get(turnId);
                if (memIds != null) sourceMemoryIds.addAll(memIds);
            }
        }

        if (sourceMemoryIds.isEmpty()) return null;

        String derivation = "neutral";
        JsonNode sourceCases = node.get("source_cases");
        if (sourceCases != null && sourceCases.isArray()) {
            for (var caseNode : sourceCases) {
                TrajectoryOutcome outcome = trajectoryOutcomeIndex.get(caseNode.asText());
                if (outcome == TrajectoryOutcome.FAILURE) { derivation = "failure"; break; }
                if (outcome == TrajectoryOutcome.SUCCESS) derivation = "success";
            }
        }

        String insight = "When " + condition + ", " + action;

        return new ReflectionEvent(agentId, tenantId, null, Instant.now(), insight,
            targetLevel, List.copyOf(sourceMemoryIds), null,
            Map.of("derivation", derivation));
    }
}
```

Note: `jakarta.alternative.Alternative` may need to be `jakarta.enterprise.inject.Alternative` — check the import used by other blocks `@Alternative` classes.

- [ ] **Step 5: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl blocks-core -Dtest=LlmReflectionSynthesizerTest -f /Users/mdproctor/claude/casehub/slots/203/blocks/pom.xml`
Expected: all 7 tests PASS

- [ ] **Step 6: Commit (blocks repo)**

```bash
git -C /Users/mdproctor/claude/casehub/slots/203/blocks add \
  blocks-core/pom.xml \
  blocks-core/src/main/java/io/casehub/blocks/agentic/social/reflection/LlmReflectionSynthesizer.java \
  blocks-core/src/test/java/io/casehub/blocks/agentic/social/reflection/LlmReflectionSynthesizerTest.java
git -C /Users/mdproctor/claude/casehub/slots/203/blocks commit -m "feat(#320): LlmReflectionSynthesizer — LLM-backed heuristic extraction

@Alternative @Priority(1) implementing ReflectionSynthesizer.
Uses TrajectoryGrouper for pre-processing, AgentProvider for LLM access.
Conditional rule output, failure-derived prioritised, dedup via existing reflections.

Refs #320"
```

---

## Batch 3: CLAUDE.md Updates and Full Build Verification

### Task 3: Update CLAUDE.md and verify cross-repo build

**Files:**
- Modify: `CLAUDE.md` (neocortex) — add TrajectoryGrouper to memory-core description

**Interfaces:**
- Consumes: all prior tasks
- Produces: updated documentation, verified green build

- [ ] **Step 1: Update neocortex CLAUDE.md**

Add to the `memory-core/` line in the Module Structure section:
```
, TrajectoryGrouper (static utility: groups flat List<Memory> into Trajectory records 
by caseId, orders by timestamp, classifies FAILURE/SUCCESS/NEUTRAL via outcome-status)
```

- [ ] **Step 2: Run full neocortex build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -f /Users/mdproctor/claude/casehub/slots/203/neocortex/pom.xml`
Expected: BUILD SUCCESS (local install needed for blocks to resolve memory-core dependency)

- [ ] **Step 3: Run full blocks build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -f /Users/mdproctor/claude/casehub/slots/203/blocks/pom.xml -DskipTests`
Then: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl blocks-core -f /Users/mdproctor/claude/casehub/slots/203/blocks/pom.xml`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 4: Commit CLAUDE.md update**

```bash
git add CLAUDE.md
git commit -m "docs(#338): update CLAUDE.md with TrajectoryGrouper in memory-core

Refs #338"
```

## References

- [2026-09-28-reflection-synthesizer-llm-design.md] — design spec this plan implements
- [memory-api/.../reflection/ReflectionSynthesizer.java] — SPI interface
- [memory-core/.../reflection/runtime/ReflectionOrchestratorCore.java] — orchestrator that calls synthesizer
- [memory-api/.../reflection/ReflectionEvent.java] — output record
- [memory-api/.../experience/ExperienceEvent.java] — sealed event hierarchy
- [memory-api/.../experience/ExperienceAttributeKeys.java] — attribute constants
- [blocks-core/.../social/InnerLifeOrchestrator.java] — blocks-side consumer of reflect()
- [GitHub neocortex#338] — focal neocortex issue
- [GitHub blocks#320] — focal blocks issue
- [GitHub blocks#311] — parent epic
