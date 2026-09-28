# LLM-Backed ReflectionSynthesizer — Heuristic Extraction from Experience Trajectories

**Issue:** casehubio/neocortex#338 (infrastructure), casehubio/blocks#320 (LLM implementation)
**Epic:** casehubio/neocortex#253, casehubio/blocks#311
**Date:** 2026-09-28

## Overview

Implement LLM-backed reflection synthesis that analyses agent experience trajectories and extracts actionable conditional heuristics. Failure-derived heuristics are prioritised based on research showing +14.3% improvement over success-derived ones (arXiv:2603.24639).

Cross-repo split following the issue-345 SPI-inversion pattern:
- **neocortex** — TrajectoryGrouper: pure Java infrastructure for grouping, ordering, and classifying experience memories into structured trajectories
- **blocks** — LlmReflectionSynthesizer: `@Alternative` implementation using AgentProvider for LLM-backed heuristic extraction

## Part 1: Neocortex — TrajectoryGrouper (memory-core)

### Location

`memory-core` module, package `io.casehub.neocortex.memory.reflection.runtime`. Pure Java, no new dependencies — this is mechanical infrastructure.

### TrajectoryGrouper

Static utility class. Groups a flat `List<Memory>` into structured trajectories for analysis by any `ReflectionSynthesizer` implementation.

```java
public final class TrajectoryGrouper {
    public static List<Trajectory> group(List<Memory> sources) { ... }
}
```

### Data types

```java
public record Trajectory(String caseId, List<TrajectoryStep> steps, 
                          TrajectoryOutcome outcome) {}

public record TrajectoryStep(String turnId, List<Memory> events, 
                              Instant timestamp) {}

public enum TrajectoryOutcome { FAILURE, SUCCESS, NEUTRAL }
```

### Grouping logic

1. Partition memories by `caseId` (null caseId → skip)
2. Within each case: sort by `createdAt` timestamp
3. Sub-group by `turn-id` attribute into `TrajectoryStep` records (step timestamp = earliest event timestamp)
4. Classify: scan for events with `event-type` = "outcome", read `outcome-status` attribute — any negative status → FAILURE, all positive → SUCCESS, absent or mixed → NEUTRAL
5. Sort output: FAILURE trajectories first, then NEUTRAL, then SUCCESS

### Edge cases

- Memories with no caseId are skipped (cannot form a trajectory)
- Memories with no turn-id are placed in a synthetic step with turnId = null
- Trajectories with no Outcome events are classified as NEUTRAL
- Single-event trajectories are included (may still yield heuristics in LLM context)

### Testing (TrajectoryGrouperTest)

Pure Java unit tests:
- Groups memories by caseId correctly
- Orders by timestamp within groups
- Sub-groups by turn-id
- Classifies FAILURE/SUCCESS/NEUTRAL based on outcome-status
- Handles missing outcome-status → NEUTRAL
- Sorts failure-first
- Skips null-caseId memories
- Handles single-event trajectories
- Handles null turn-id

## Part 2: Blocks — LlmReflectionSynthesizer

### Location

Blocks repo. `@Alternative @Priority(1) @ApplicationScoped`, implements `ReflectionSynthesizer`. Uses `Instance<AgentProvider>` for graceful degradation — returns empty list when no LLM is available.

### Dependencies

| Dependency | Purpose |
|---|---|
| `casehub-neocortex-memory-api` | ReflectionSynthesizer SPI, Memory, ExperienceAttributeKeys |
| `casehub-neocortex-memory-core` | TrajectoryGrouper |
| `casehub-platform-agent-api` | AgentProvider for LLM access |

### Data Flow

```
ReflectionOrchestratorCore.reflect()
  │
  ├─ queries CaseMemoryStore for experience memories
  ├─ queries existing reflections (for dedup context)
  │
  └─ calls LlmReflectionSynthesizer.synthesize()
       │
       ├─ 1. TrajectoryGrouper.group(sources) → List<Trajectory>
       │     (failure-first ordering, caseId grouping)
       │
       ├─ 2. Build LLM prompt
       │     ├─ System prompt: extraction rules, JSON schema
       │     ├─ Trajectories: failure-first, numbered steps
       │     ├─ Existing reflections: dedup context
       │     └─ Instruction: conditional rules, prioritise failures
       │
       ├─ 3. AgentProvider.chat() → JSON response
       │
       └─ 4. Parse response → List<ReflectionEvent>
             ├─ Map source_turns → memory IDs via trajectory index
             ├─ Build "When {condition}, {action}" insight strings
             └─ Tag metadata.derivation = failure/success/neutral
```

### LLM Prompt Structure

**System prompt:**
```
You are a reflection agent. Analyse experience trajectories and extract 
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
- Prioritise failure-derived heuristics
- Reference source cases and turns for traceability
- Do not repeat existing insights (listed below)
- Respond with valid JSON only
```

**User prompt:** Trajectories rendered as numbered blocks with case ID, steps ordered by turn, events by type (observation → action → outcome). Outcome status displayed when present. Existing reflections listed at the end as "Already known insights — do not repeat."

### Dedup Strategy

Query existing reflections via `ReflectionQuery.forAgent()` before calling the synthesizer. Pass them as context in the prompt (up to a configurable max, default 20). The LLM is instructed not to repeat them.

Note: this requires the orchestrator to pass existing reflections to the synthesizer. Two options:
1. The synthesizer queries the store itself (needs CaseMemoryStore injected)
2. The orchestrator passes them via a new parameter or context object

Option 1 is simpler and doesn't change the SPI — the synthesizer receives existing reflections by querying the store directly.

### Mapping to ReflectionEvent

| LLM output | ReflectionEvent field | Transformation |
|---|---|---|
| condition + action | insight | `"When {condition}, {action}"` |
| source_turns | sourceMemoryIds | Lookup turn-id → memory IDs via trajectory index |
| — | level | Pass-through from `targetLevel` |
| — | confidence | null (defaults to 0.5 via ReflectionEvents formula) |
| — | agentId, tenantId | Pass-through from synthesize() parameters |
| — | metadata.derivation | "failure"/"success"/"neutral" from source trajectory |

### Error Handling

- AgentProvider unavailable → return empty list
- Malformed JSON → log warning, return empty list
- Missing required fields → skip that heuristic, continue parsing others
- source_turns referencing non-existent turn IDs → skip that heuristic
- Empty heuristics array → return empty list (valid response)

### Configuration

```properties
casehub.reflection.llm.max-heuristics=5
casehub.reflection.llm.max-existing-reflections=20
```

### Testing

1. **LlmReflectionSynthesizerTest** — mock AgentProvider:
   - End-to-end with canned LLM responses
   - Returns empty list when AgentProvider unavailable
   - Returns empty list when no source memories
   - Correctly uses TrajectoryGrouper for pre-processing
   - Parses valid JSON responses
   - Handles malformed JSON gracefully
   - Handles missing fields, invalid source references
   - Dedup: existing reflections included in prompt, not repeated in output

## CLAUDE.md Updates

### Neocortex
Add TrajectoryGrouper to memory-core description:
```
memory-core/ — ... TrajectoryGrouper (static utility: groups flat List<Memory> 
    into Trajectory records by caseId, orders by timestamp, classifies 
    FAILURE/SUCCESS/NEUTRAL via outcome-status)
```

### Blocks
Add LlmReflectionSynthesizer to the appropriate section.

## References

- ReflectionSynthesizer SPI: `memory-api/.../reflection/ReflectionSynthesizer.java`
- ReflectionOrchestratorCore: `memory-core/.../reflection/runtime/ReflectionOrchestratorCore.java`
- NoOpReflectionSynthesizer: `memory/.../reflection/runtime/NoOpReflectionSynthesizer.java`
- ReflectionEvent record: `memory-api/.../reflection/ReflectionEvent.java`
- ExperienceEvent sealed hierarchy: `memory-api/.../experience/ExperienceEvent.java`
- ExperienceAttributeKeys: `memory-api/.../experience/ExperienceAttributeKeys.java`
- Issue-345 SPI-inversion pattern: neocortex defines SPIs, blocks provides LLM implementations
- AgentProvider pattern in blocks: `@Alternative @Priority` with `Instance<AgentProvider>`
- arXiv:2603.24639 — Experiential Reflective Learning: failure-derived heuristic prioritisation (+14.3%)
- arXiv:2506.06698 — Contextual Experience Replay: trajectory-based learning
- Generative Agents (Park et al.) — reflection architecture (free-form, not conditional)
- Downstream: blocks#314 — reflection consumption in agent reasoning
