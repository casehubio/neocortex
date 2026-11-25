# Additional Cognitive Capabilities — Design Spec

**Issue:** casehubio/neocortex#1237
**Branch:** issue-1237-additional-capabilities
**Module:** cognitive-runtime

## Overview

Add 4 new `CognitiveCapability` implementations to the cognitive-runtime module. Each reads persisted state from MindMap or CaseMemoryStore and renders a prompt section. Each disables itself when the full cognition module is active (mutual exclusion via D5).

## Design Principle

cognitive-runtime is a **read-persisted-state** layer. It queries graph and memory stores for data previously written by the cognition tick loop, consolidation scheduler, or other pipelines. It never computes new cognitive state at render time.

When the full cognition module is on the classpath, its prompt sections provide higher-fidelity runtime versions of the same content. The lightweight cognitive-runtime capabilities yield via `isActive()`.

## Capability Specifications

### 1. NeedsCapability

**BlockTag:** NEEDS(230) | **SectionTier:** CONTEXTUAL

**Data source:** MindMap COGNITIVE subgraph nodes filtered by `cognitiveKind` property:
- `"need-satisfaction"` nodes → `tier` and `satisfaction` properties (NeedTier: SAFETY, TASKS, SOCIAL, SELF_EXPRESSION, UNDERSTANDING)
- `"drive-intensity"` nodes → `drive-type` property

Scoped to the agent via `agent-id` node property.

**Rendering:** For each need tier with data, render felt-state text (e.g., "Your sense of safety feels secure"). Order by tier hierarchy. Return null when no need-satisfaction nodes exist.

**Dependencies:** MindMapStore (already a dep via GoalCapability)

**Constructor:** `NeedsCapability(MindMapStore mindMapStore)`

### 2. AppraisalCapability

**BlockTag:** APPRAISAL(220) | **SectionTier:** CONTEXTUAL

**Data source:** MindMap COGNITIVE subgraph — mood-related nodes or affect-trajectory data persisted by the mood orchestrator. Query nodes with domain="affect" or use `AffectTrajectoryDecorator` PAD values stored on nodes.

Alternatively, scan `CaseMemoryStore` with domain="mood" for the most recent `MoodState` entries (PAD values stored as attributes by `MoodEvents`).

**Rendering:** Render current emotional context from persisted PAD values. Map pleasure/arousal/dominance to natural language descriptors (e.g., high pleasure + low arousal → "calm contentment"). Return null when no mood data exists.

**Dependencies:** CaseMemoryStore (already a dep via MemoryCapability) or MindMapStore

**Constructor:** `AppraisalCapability(CaseMemoryStore memoryStore)`

### 3. InnerThoughtsCapability

**BlockTag:** REFLECTION(510) | **SectionTier:** SUPPLEMENTARY

**Data source:** `CaseMemoryStore.scan()` with domain="reflection". Same pattern as MemoryCapability (domain="experience").

**Rendering:** Render recent reflections as inner voice. Format: "Recent reflections:\n- {text}". Limit to 5 most recent. Return null when no reflection memories exist.

**Dependencies:** CaseMemoryStore (already a dep via MemoryCapability)

**Constructor:** `InnerThoughtsCapability(CaseMemoryStore memoryStore)`

### 4. ConsolidationCapability

**BlockTag:** CONSOLIDATION(520) | **SectionTier:** SUPPLEMENTARY

**Data source:** MindMap COGNITIVE subgraph — all nodes, grouped by trait for rendering:
- `"graduated-sub-thought"` → graduated experience entities (from SubThoughtConsolidationPhase)
- `"Summary"` → community summaries (from CommunitySummaryPhase)
- `"CuriosityDescriptionGenerated"` → curiosity descriptions (from CuriosityDescriptionGenerationPhase)

**Rendering:** Group nodes by trait, render each subsection:
```
Recent learning:
Graduated: {node names}
Summaries: {node names}
Curiosity: {node names}
```
Limit to 10 most recent nodes total. Return null when no COGNITIVE subgraph nodes exist.

**Dependencies:** MindMapStore (already a dep via GoalCapability)

**Constructor:** `ConsolidationCapability(MindMapStore mindMapStore)`

## Mutual Exclusion (D5)

`CognitiveAvatarProducer` detects whether the cognition module's prompt sections are available. It passes a `cognitionActive` boolean to each new capability at construction time.

Each new capability implements:
```java
@Override
public boolean isActive() {
    return !cognitionActive;
}
```

The existing 6 capabilities (Briefing, Goal, Temporal, Memory, EntityKnowledge, Disposition) are NOT affected — they provide complementary static/structural data that doesn't duplicate cognition sections.

**Detection mechanism:** `CognitiveAvatarProducer` checks `Instance<CognitionPromptRenderer>` or a marker bean from the cognition module. The specific marker will be determined during implementation — the simplest option that the cognition module already exposes.

## CognitiveAvatarProducer Changes

```java
private CognitiveAvatar buildAndInitAvatar(String agentId, String tenantId) {
    var profile = registry.forAgentOrDefaults(agentId);
    boolean cognitionActive = /* detect cognition module */;
    var capabilities = new ArrayList<CognitiveCapability>();

    // Existing capabilities (always active)
    if (profile.briefing() != null) {
        capabilities.add(new BriefingCapability(profile.briefing()));
    }
    if (mindMapStore.isResolvable()) {
        capabilities.add(new GoalCapability(mindMapStore.get()));
        capabilities.add(new TemporalCapability(mindMapStore.get()));
    }
    if (memoryStore.isResolvable()) {
        capabilities.add(new MemoryCapability(memoryStore.get()));
    }
    if (cognitiveProfile.isResolvable()) {
        capabilities.add(new EntityKnowledgeCapability(cognitiveProfile.get()));
    }
    capabilities.add(new DispositionCapability(profile));

    // New capabilities (yield when cognition is active)
    if (mindMapStore.isResolvable()) {
        capabilities.add(new NeedsCapability(mindMapStore.get(), cognitionActive));
        capabilities.add(new ConsolidationCapability(mindMapStore.get(), cognitionActive));
    }
    if (memoryStore.isResolvable()) {
        capabilities.add(new AppraisalCapability(memoryStore.get(), cognitionActive));
        capabilities.add(new InnerThoughtsCapability(memoryStore.get(), cognitionActive));
    }

    var avatar = new CognitiveAvatar(profile, capabilities);
    avatar.initialize(agentId, tenantId);
    return avatar;
}
```

## Testing Strategy

Each capability gets:
1. Unit tests (same pattern as existing capability tests) — render with seeded data, null with empty stores, BlockTag assertion, SectionTier assertion, name assertion
2. `isActive()` test — verify it returns false when `cognitionActive=true`

The existing `CapabilityCompositionTest` from #1238 will be extended to include the 4 new capabilities in the all-capabilities composition tests.

## No New Module Dependencies

All 4 capabilities use types already on cognitive-runtime's classpath:
- `MindMapStore`, `MindMapNode`, `SubgraphTypes` — from mindmap-api
- `CaseMemoryStore`, `MemoryScanRequest`, `MemoryDomain` — from memory-api
- `BlockTag`, `CognitionPromptRenderer`, `SectionTier` — from cognition-api

Zero new `<dependency>` entries in cognitive-runtime's pom.xml.

## References

- CognitiveCapability SPI: `cognitive-runtime/.../CognitiveCapability.java`
- CognitiveAvatarProducer: `cognitive-runtime/.../CognitiveAvatarProducer.java`
- NeedsPyramidPromptSection: `cognition/.../NeedsPyramidPromptSection.java` — reference for needs query pattern
- MoodEvents/MoodAttributeKeys: `memory-api/.../MoodEvents.java` — PAD storage format
- SubThoughtConsolidationPhase: `mindmap-intelligence/.../SubThoughtConsolidationPhase.java` — `"graduated-sub-thought"` trait
- CommunitySummaryPhase: `mindmap-intelligence/.../CommunitySummaryPhase.java` — `"Summary"` trait
- CuriosityDescriptionGenerationPhase: `mindmap-intelligence/.../CuriosityDescriptionGenerationPhase.java` — `"CuriosityDescriptionGenerated"` trait
- BlockTag enum: `cognition-api/.../BlockTag.java`
- decisions.md: `specs/issue-1237-additional-capabilities/decisions.md` — D1-D6
