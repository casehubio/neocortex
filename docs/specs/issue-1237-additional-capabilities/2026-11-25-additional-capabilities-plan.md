# Additional Cognitive Capabilities Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #1237 — feat(cognitive-runtime): additional capabilities — consolidation, appraisal, inner thoughts, needs
**Issue group:** #1237

**Goal:** Add 4 new CognitiveCapability implementations that read persisted cognitive state and yield when the full cognition module is active.

**Architecture:** Each capability follows the established read-and-render pattern — constructor takes a store dependency plus a `cognitionActive` boolean, `isActive()` returns `!cognitionActive`, and the prompt section queries the store and formats the results. CognitiveAvatarProducer wires them with store availability and cognition-active detection.

**Tech Stack:** Java 21, JUnit 5, InMemoryMindMapStore, InMemoryMemoryStore

## Global Constraints

- Zero new `<dependency>` entries in cognitive-runtime's pom.xml
- No dependency on the `cognition` module (only cognition-api)
- TDD: every capability test-first
- Each capability must degrade gracefully (return null) when stores are empty

---

## Batch 1: Store-backed capabilities — InnerThoughts + Appraisal

### Task 1: InnerThoughtsCapability

**Files:**
- Create: `cognitive-runtime/src/main/java/io/casehub/neocortex/cognitive/runtime/InnerThoughtsCapability.java`
- Test: `cognitive-runtime/src/test/java/io/casehub/neocortex/cognitive/runtime/InnerThoughtsCapabilityTest.java`

**Interfaces:**
- Consumes: `CaseMemoryStore.scan(MemoryScanRequest)`, `CognitiveCapability`, `CognitiveAvatarContext`
- Produces: `InnerThoughtsCapability` — constructor `(CaseMemoryStore memoryStore, boolean cognitionActive)`

- [ ] **Step 1: Write failing tests**

```java
package io.casehub.neocortex.cognitive.runtime;

import io.casehub.neocortex.cognition.prompt.BlockTag;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.SectionTier;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InnerThoughtsCapabilityTest {

    private static final String TENANT = "test-tenant";
    private static final CurrentPrincipal PRINCIPAL = new CurrentPrincipal() {
        @Override public String actorId() { return "test"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return false; }
    };

    private InMemoryMemoryStore memoryStore;
    private InnerThoughtsCapability capability;

    @BeforeEach
    void setUp() {
        memoryStore = new InMemoryMemoryStore(PRINCIPAL);
        capability = new InnerThoughtsCapability(memoryStore, false);
        capability.initialize(new CognitiveAvatarContext("agent-1", TENANT,
            io.casehub.neocortex.cognitive.index.CognitiveDefaults.empty("agent-1")));
    }

    @Test
    void rendersRecentReflections() {
        memoryStore.store(MemoryInput.of(
            Subject.of("agent", "agent-1"), new MemoryDomain("reflection"),
            TENANT, "I notice I tend to avoid confrontation"));
        memoryStore.store(MemoryInput.of(
            Subject.of("agent", "agent-1"), new MemoryDomain("reflection"),
            TENANT, "My patience seems to grow when I listen first"));

        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNotNull(content);
        assertTrue(content.contains("avoid confrontation"));
        assertTrue(content.contains("patience"));
    }

    @Test
    void returnsNullWhenNoReflections() {
        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNull(content);
    }

    @Test
    void filtersToReflectionDomain() {
        memoryStore.store(MemoryInput.of(
            Subject.of("agent", "agent-1"), new MemoryDomain("reflection"),
            TENANT, "A reflection thought"));
        memoryStore.store(MemoryInput.of(
            Subject.of("agent", "agent-1"), new MemoryDomain("experience"),
            TENANT, "An experience memory"));

        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertTrue(content.contains("reflection thought"));
        assertFalse(content.contains("experience memory"));
    }

    @Test
    void inactiveWhenCognitionActive() {
        var inactive = new InnerThoughtsCapability(memoryStore, true);
        assertFalse(inactive.isActive());
    }

    @Test
    void activeWhenCognitionNotActive() {
        assertTrue(capability.isActive());
    }

    @Test
    void nameIsInnerThoughts() {
        assertEquals("inner-thoughts", capability.name());
    }

    @Test
    void blockTagIsReflection() {
        assertEquals(BlockTag.REFLECTION, capability.promptSections().getFirst().blockTag());
    }

    @Test
    void sectionTierIsSupplementary() {
        assertEquals(SectionTier.SUPPLEMENTARY, capability.promptSections().getFirst().sectionTier());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-runtime -Dtest=InnerThoughtsCapabilityTest -Dsurefire.useFile=false`
Expected: Compilation failure — InnerThoughtsCapability does not exist

- [ ] **Step 3: Write minimal implementation**

```java
package io.casehub.neocortex.cognitive.runtime;

import io.casehub.neocortex.cognition.prompt.BlockTag;
import io.casehub.neocortex.cognition.prompt.CognitionPromptRenderer;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.SectionTier;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryScanRequest;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class InnerThoughtsCapability implements CognitiveCapability {

    private final CaseMemoryStore memoryStore;
    private final boolean cognitionActive;
    private String tenantId;

    public InnerThoughtsCapability(CaseMemoryStore memoryStore, boolean cognitionActive) {
        this.memoryStore = memoryStore;
        this.cognitionActive = cognitionActive;
    }

    @Override
    public String name() { return "inner-thoughts"; }

    @Override
    public void initialize(CognitiveAvatarContext context) {
        this.tenantId = context.tenantId();
    }

    @Override
    public boolean isActive() { return !cognitionActive; }

    @Override
    public List<CognitionPromptRenderer> promptSections() {
        return List.of(new InnerThoughtsPromptSection());
    }

    private class InnerThoughtsPromptSection implements CognitionPromptRenderer {

        @Override
        public @Nullable String render(CognitionRenderContext ctx) {
            if (tenantId == null) return null;
            var request = new MemoryScanRequest(tenantId, "reflection", null, null, 5, null);
            var memories = memoryStore.scan(request);
            if (memories.isEmpty()) return null;

            var sb = new StringBuilder("Recent reflections:\n");
            for (var memory : memories) {
                sb.append("- ").append(memory.text()).append("\n");
            }
            return sb.toString().strip();
        }

        @Override
        public BlockTag blockTag() { return BlockTag.REFLECTION; }

        @Override
        public @Nullable SectionTier sectionTier() { return SectionTier.SUPPLEMENTARY; }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-runtime -Dtest=InnerThoughtsCapabilityTest -Dsurefire.useFile=false`
Expected: 8 tests PASS

- [ ] **Step 5: Commit**

```bash
git add cognitive-runtime/src/main/java/io/casehub/neocortex/cognitive/runtime/InnerThoughtsCapability.java \
      cognitive-runtime/src/test/java/io/casehub/neocortex/cognitive/runtime/InnerThoughtsCapabilityTest.java
git commit -m "feat(cognitive-runtime): add InnerThoughtsCapability — reflection memory rendering Refs #1237"
```

### Task 2: AppraisalCapability

**Files:**
- Create: `cognitive-runtime/src/main/java/io/casehub/neocortex/cognitive/runtime/AppraisalCapability.java`
- Test: `cognitive-runtime/src/test/java/io/casehub/neocortex/cognitive/runtime/AppraisalCapabilityTest.java`

**Interfaces:**
- Consumes: `CaseMemoryStore.scan(MemoryScanRequest)`, `Memory.pleasure()`, `Memory.arousal()`, `Memory.dominance()`
- Produces: `AppraisalCapability` — constructor `(CaseMemoryStore memoryStore, boolean cognitionActive)`

- [ ] **Step 1: Write failing tests**

```java
package io.casehub.neocortex.cognitive.runtime;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.cognition.prompt.BlockTag;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.SectionTier;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AppraisalCapabilityTest {

    private static final String TENANT = "test-tenant";
    private static final CurrentPrincipal PRINCIPAL = new CurrentPrincipal() {
        @Override public String actorId() { return "test"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return false; }
    };

    private InMemoryMemoryStore memoryStore;
    private AppraisalCapability capability;

    @BeforeEach
    void setUp() {
        memoryStore = new InMemoryMemoryStore(PRINCIPAL);
        capability = new AppraisalCapability(memoryStore, false);
        capability.initialize(new CognitiveAvatarContext("agent-1", TENANT,
            io.casehub.neocortex.cognitive.index.CognitiveDefaults.empty("agent-1")));
    }

    @Test
    void rendersMoodFromStoredPadValues() {
        var input = MemoryInput.of(
            Subject.of("agent", "agent-1"), new MemoryDomain("mood"), TENANT, "mood snapshot")
            .withPad(0.6, -0.2, 0.3);
        memoryStore.store(input);

        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNotNull(content);
        assertTrue(content.contains("Emotional"), "Should contain emotional context header");
    }

    @Test
    void returnsNullWhenNoMoodData() {
        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNull(content);
    }

    @Test
    void inactiveWhenCognitionActive() {
        var inactive = new AppraisalCapability(memoryStore, true);
        assertFalse(inactive.isActive());
    }

    @Test
    void activeWhenCognitionNotActive() {
        assertTrue(capability.isActive());
    }

    @Test
    void nameIsAppraisal() {
        assertEquals("appraisal", capability.name());
    }

    @Test
    void blockTagIsAppraisal() {
        assertEquals(BlockTag.APPRAISAL, capability.promptSections().getFirst().blockTag());
    }

    @Test
    void sectionTierIsContextual() {
        assertEquals(SectionTier.CONTEXTUAL, capability.promptSections().getFirst().sectionTier());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-runtime -Dtest=AppraisalCapabilityTest -Dsurefire.useFile=false`
Expected: Compilation failure — AppraisalCapability does not exist

- [ ] **Step 3: Check MemoryInput.withPad() exists**

Verify `MemoryInput` has a `withPad(Double, Double, Double)` method. If not, use the full constructor with PAD fields. Adjust test accordingly.

- [ ] **Step 4: Write minimal implementation**

```java
package io.casehub.neocortex.cognitive.runtime;

import io.casehub.neocortex.cognition.prompt.BlockTag;
import io.casehub.neocortex.cognition.prompt.CognitionPromptRenderer;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.SectionTier;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryScanRequest;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class AppraisalCapability implements CognitiveCapability {

    private final CaseMemoryStore memoryStore;
    private final boolean cognitionActive;
    private String tenantId;

    public AppraisalCapability(CaseMemoryStore memoryStore, boolean cognitionActive) {
        this.memoryStore = memoryStore;
        this.cognitionActive = cognitionActive;
    }

    @Override
    public String name() { return "appraisal"; }

    @Override
    public void initialize(CognitiveAvatarContext context) {
        this.tenantId = context.tenantId();
    }

    @Override
    public boolean isActive() { return !cognitionActive; }

    @Override
    public List<CognitionPromptRenderer> promptSections() {
        return List.of(new AppraisalPromptSection());
    }

    private class AppraisalPromptSection implements CognitionPromptRenderer {

        @Override
        public @Nullable String render(CognitionRenderContext ctx) {
            if (tenantId == null) return null;
            var request = new MemoryScanRequest(tenantId, "mood", null, null, 1, null);
            var memories = memoryStore.scan(request);
            if (memories.isEmpty()) return null;

            var mood = memories.getFirst();
            if (mood.pleasure() == null && mood.arousal() == null && mood.dominance() == null) {
                return null;
            }

            var sb = new StringBuilder("Emotional state:\n");
            describePad(sb, mood);
            return sb.toString().strip();
        }

        @Override
        public BlockTag blockTag() { return BlockTag.APPRAISAL; }

        @Override
        public @Nullable SectionTier sectionTier() { return SectionTier.CONTEXTUAL; }
    }

    static void describePad(StringBuilder sb, Memory mood) {
        Double p = mood.pleasure();
        Double a = mood.arousal();
        Double d = mood.dominance();

        if (p != null) {
            sb.append("- Valence: ").append(p > 0.3 ? "positive" : p < -0.3 ? "negative" : "neutral").append("\n");
        }
        if (a != null) {
            sb.append("- Arousal: ").append(a > 0.3 ? "elevated" : a < -0.3 ? "low" : "moderate").append("\n");
        }
        if (d != null) {
            sb.append("- Sense of control: ").append(d > 0.3 ? "strong" : d < -0.3 ? "weak" : "balanced").append("\n");
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-runtime -Dtest=AppraisalCapabilityTest -Dsurefire.useFile=false`
Expected: 7 tests PASS

- [ ] **Step 6: Commit**

```bash
git add cognitive-runtime/src/main/java/io/casehub/neocortex/cognitive/runtime/AppraisalCapability.java \
      cognitive-runtime/src/test/java/io/casehub/neocortex/cognitive/runtime/AppraisalCapabilityTest.java
git commit -m "feat(cognitive-runtime): add AppraisalCapability — mood state rendering Refs #1237"
```

## Batch 2: MindMap-backed capabilities — Needs + Consolidation

### Task 3: NeedsCapability

**Files:**
- Create: `cognitive-runtime/src/main/java/io/casehub/neocortex/cognitive/runtime/NeedsCapability.java`
- Test: `cognitive-runtime/src/test/java/io/casehub/neocortex/cognitive/runtime/NeedsCapabilityTest.java`

**Interfaces:**
- Consumes: `MindMapStore.nodesIn(String subgraphId, String tenantId)`, `MindMapNode.property(String)`
- Produces: `NeedsCapability` — constructor `(MindMapStore mindMapStore, boolean cognitionActive)`

- [ ] **Step 1: Write failing tests**

```java
package io.casehub.neocortex.cognitive.runtime;

import io.casehub.neocortex.cognition.prompt.BlockTag;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.SectionTier;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NeedsCapabilityTest {

    private static final String TENANT = "test-tenant";

    private InMemoryMindMapStore mindMapStore;
    private NeedsCapability capability;

    @BeforeEach
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        capability = new NeedsCapability(mindMapStore, false);
        capability.initialize(new CognitiveAvatarContext("agent-1", TENANT,
            io.casehub.neocortex.cognitive.index.CognitiveDefaults.empty("agent-1")));
    }

    @Test
    void rendersNeedSatisfactionNodes() {
        mindMapStore.addNode(NodeInput.of("safety-need", SubgraphTypes.COGNITIVE)
            .withProperties(Map.of(
                "cognitiveKind", "need-satisfaction",
                "tier", "SAFETY",
                "satisfaction", "0.8",
                "agent-id", "agent-1")), TENANT);

        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNotNull(content);
        assertTrue(content.toLowerCase().contains("safety"));
    }

    @Test
    void returnsNullWhenNoNeedsData() {
        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNull(content);
    }

    @Test
    void filtersToAgentId() {
        mindMapStore.addNode(NodeInput.of("other-agent-need", SubgraphTypes.COGNITIVE)
            .withProperties(Map.of(
                "cognitiveKind", "need-satisfaction",
                "tier", "TASKS",
                "satisfaction", "0.5",
                "agent-id", "other-agent")), TENANT);

        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNull(content, "Should not render needs for other agents");
    }

    @Test
    void inactiveWhenCognitionActive() {
        var inactive = new NeedsCapability(mindMapStore, true);
        assertFalse(inactive.isActive());
    }

    @Test
    void activeWhenCognitionNotActive() {
        assertTrue(capability.isActive());
    }

    @Test
    void nameIsNeeds() {
        assertEquals("needs", capability.name());
    }

    @Test
    void blockTagIsNeeds() {
        assertEquals(BlockTag.NEEDS, capability.promptSections().getFirst().blockTag());
    }

    @Test
    void sectionTierIsContextual() {
        assertEquals(SectionTier.CONTEXTUAL, capability.promptSections().getFirst().sectionTier());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-runtime -Dtest=NeedsCapabilityTest -Dsurefire.useFile=false`
Expected: Compilation failure — NeedsCapability does not exist

- [ ] **Step 3: Write minimal implementation**

```java
package io.casehub.neocortex.cognitive.runtime;

import io.casehub.neocortex.cognition.prompt.BlockTag;
import io.casehub.neocortex.cognition.prompt.CognitionPromptRenderer;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.SectionTier;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class NeedsCapability implements CognitiveCapability {

    private final MindMapStore mindMapStore;
    private final boolean cognitionActive;
    private String tenantId;
    private String agentId;

    public NeedsCapability(MindMapStore mindMapStore, boolean cognitionActive) {
        this.mindMapStore = mindMapStore;
        this.cognitionActive = cognitionActive;
    }

    @Override
    public String name() { return "needs"; }

    @Override
    public void initialize(CognitiveAvatarContext context) {
        this.tenantId = context.tenantId();
        this.agentId = context.agentId();
    }

    @Override
    public boolean isActive() { return !cognitionActive; }

    @Override
    public List<CognitionPromptRenderer> promptSections() {
        return List.of(new NeedsPromptSection());
    }

    private class NeedsPromptSection implements CognitionPromptRenderer {

        @Override
        public @Nullable String render(CognitionRenderContext ctx) {
            if (tenantId == null) return null;
            var nodes = mindMapStore.nodesIn(SubgraphTypes.COGNITIVE, tenantId);
            var needNodes = nodes.stream()
                .filter(n -> "need-satisfaction".equals(n.property("cognitiveKind").orElse(null)))
                .filter(n -> agentId == null || agentId.equals(n.property("agent-id").orElse(null)))
                .toList();
            if (needNodes.isEmpty()) return null;

            var sb = new StringBuilder("Current needs:\n");
            for (var node : needNodes) {
                var tier = node.property("tier").orElse("unknown");
                var satisfaction = node.property("satisfaction").orElse("unknown");
                sb.append("- ").append(tier.toLowerCase().replace('_', ' '))
                    .append(": ").append(describeSatisfaction(satisfaction)).append("\n");
            }
            return sb.toString().strip();
        }

        @Override
        public BlockTag blockTag() { return BlockTag.NEEDS; }

        @Override
        public @Nullable SectionTier sectionTier() { return SectionTier.CONTEXTUAL; }
    }

    private static String describeSatisfaction(String value) {
        try {
            double v = Double.parseDouble(value);
            if (v >= 0.7) return "well met";
            if (v >= 0.4) return "partially met";
            return "unmet";
        } catch (NumberFormatException e) {
            return value;
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-runtime -Dtest=NeedsCapabilityTest -Dsurefire.useFile=false`
Expected: 8 tests PASS

- [ ] **Step 5: Commit**

```bash
git add cognitive-runtime/src/main/java/io/casehub/neocortex/cognitive/runtime/NeedsCapability.java \
      cognitive-runtime/src/test/java/io/casehub/neocortex/cognitive/runtime/NeedsCapabilityTest.java
git commit -m "feat(cognitive-runtime): add NeedsCapability — need satisfaction rendering Refs #1237"
```

### Task 4: ConsolidationCapability

**Files:**
- Create: `cognitive-runtime/src/main/java/io/casehub/neocortex/cognitive/runtime/ConsolidationCapability.java`
- Test: `cognitive-runtime/src/test/java/io/casehub/neocortex/cognitive/runtime/ConsolidationCapabilityTest.java`

**Interfaces:**
- Consumes: `MindMapStore.nodesIn(String subgraphId, String tenantId)`, `MindMapNode.traits()`, `MindMapNode.name()`
- Produces: `ConsolidationCapability` — constructor `(MindMapStore mindMapStore, boolean cognitionActive)`

- [ ] **Step 1: Write failing tests**

```java
package io.casehub.neocortex.cognitive.runtime;

import io.casehub.neocortex.cognition.prompt.BlockTag;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.SectionTier;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConsolidationCapabilityTest {

    private static final String TENANT = "test-tenant";

    private InMemoryMindMapStore mindMapStore;
    private ConsolidationCapability capability;

    @BeforeEach
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        capability = new ConsolidationCapability(mindMapStore, false);
        capability.initialize(new CognitiveAvatarContext("agent-1", TENANT,
            io.casehub.neocortex.cognitive.index.CognitiveDefaults.empty("agent-1")));
    }

    @Test
    void rendersGraduatedNodes() {
        mindMapStore.addNode(NodeInput.of("Pattern recognition", SubgraphTypes.COGNITIVE)
            .withTraits(Set.of("graduated-sub-thought")), TENANT);

        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNotNull(content);
        assertTrue(content.contains("Pattern recognition"));
    }

    @Test
    void rendersSummaryNodes() {
        mindMapStore.addNode(NodeInput.of("Community overview", SubgraphTypes.COGNITIVE)
            .withTraits(Set.of("Summary")), TENANT);

        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNotNull(content);
        assertTrue(content.contains("Community overview"));
    }

    @Test
    void rendersCuriosityNodes() {
        mindMapStore.addNode(NodeInput.of("Information gap about topology", SubgraphTypes.COGNITIVE)
            .withTraits(Set.of("CuriosityDescriptionGenerated")), TENANT);

        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNotNull(content);
        assertTrue(content.contains("topology"));
    }

    @Test
    void groupsByTraitType() {
        mindMapStore.addNode(NodeInput.of("Graduated entity", SubgraphTypes.COGNITIVE)
            .withTraits(Set.of("graduated-sub-thought")), TENANT);
        mindMapStore.addNode(NodeInput.of("Summary node", SubgraphTypes.COGNITIVE)
            .withTraits(Set.of("Summary")), TENANT);

        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNotNull(content);
        assertTrue(content.contains("Graduated entity"));
        assertTrue(content.contains("Summary node"));
    }

    @Test
    void returnsNullWhenNoCognitiveNodes() {
        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNull(content);
    }

    @Test
    void skipsNodesWithoutConsolidationTraits() {
        mindMapStore.addNode(NodeInput.of("Plain node", SubgraphTypes.COGNITIVE), TENANT);

        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNull(content);
    }

    @Test
    void inactiveWhenCognitionActive() {
        var inactive = new ConsolidationCapability(mindMapStore, true);
        assertFalse(inactive.isActive());
    }

    @Test
    void activeWhenCognitionNotActive() {
        assertTrue(capability.isActive());
    }

    @Test
    void nameIsConsolidation() {
        assertEquals("consolidation", capability.name());
    }

    @Test
    void blockTagIsConsolidation() {
        assertEquals(BlockTag.CONSOLIDATION, capability.promptSections().getFirst().blockTag());
    }

    @Test
    void sectionTierIsSupplementary() {
        assertEquals(SectionTier.SUPPLEMENTARY, capability.promptSections().getFirst().sectionTier());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-runtime -Dtest=ConsolidationCapabilityTest -Dsurefire.useFile=false`
Expected: Compilation failure — ConsolidationCapability does not exist

- [ ] **Step 3: Write minimal implementation**

```java
package io.casehub.neocortex.cognitive.runtime;

import io.casehub.neocortex.cognition.prompt.BlockTag;
import io.casehub.neocortex.cognition.prompt.CognitionPromptRenderer;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.SectionTier;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ConsolidationCapability implements CognitiveCapability {

    static final String TRAIT_GRADUATED = "graduated-sub-thought";
    static final String TRAIT_SUMMARY = "Summary";
    static final String TRAIT_CURIOSITY = "CuriosityDescriptionGenerated";
    static final Set<String> CONSOLIDATION_TRAITS = Set.of(TRAIT_GRADUATED, TRAIT_SUMMARY, TRAIT_CURIOSITY);

    private final MindMapStore mindMapStore;
    private final boolean cognitionActive;
    private String tenantId;

    public ConsolidationCapability(MindMapStore mindMapStore, boolean cognitionActive) {
        this.mindMapStore = mindMapStore;
        this.cognitionActive = cognitionActive;
    }

    @Override
    public String name() { return "consolidation"; }

    @Override
    public void initialize(CognitiveAvatarContext context) {
        this.tenantId = context.tenantId();
    }

    @Override
    public boolean isActive() { return !cognitionActive; }

    @Override
    public List<CognitionPromptRenderer> promptSections() {
        return List.of(new ConsolidationPromptSection());
    }

    private class ConsolidationPromptSection implements CognitionPromptRenderer {

        @Override
        public @Nullable String render(CognitionRenderContext ctx) {
            if (tenantId == null) return null;
            var allNodes = mindMapStore.nodesIn(SubgraphTypes.COGNITIVE, tenantId);

            var graduated = new ArrayList<MindMapNode>();
            var summaries = new ArrayList<MindMapNode>();
            var curiosity = new ArrayList<MindMapNode>();

            for (var node : allNodes) {
                var traits = node.traits();
                if (traits.contains(TRAIT_GRADUATED)) graduated.add(node);
                else if (traits.contains(TRAIT_SUMMARY)) summaries.add(node);
                else if (traits.contains(TRAIT_CURIOSITY)) curiosity.add(node);
            }

            if (graduated.isEmpty() && summaries.isEmpty() && curiosity.isEmpty()) {
                return null;
            }

            var sb = new StringBuilder("Recent learning:\n");
            appendSection(sb, "Graduated insights", graduated, 5);
            appendSection(sb, "Summaries", summaries, 3);
            appendSection(sb, "Curiosity", curiosity, 3);
            return sb.toString().strip();
        }

        @Override
        public BlockTag blockTag() { return BlockTag.CONSOLIDATION; }

        @Override
        public @Nullable SectionTier sectionTier() { return SectionTier.SUPPLEMENTARY; }
    }

    private static void appendSection(StringBuilder sb, String header,
                                       List<MindMapNode> nodes, int limit) {
        if (nodes.isEmpty()) return;
        sb.append(header).append(":\n");
        nodes.stream().limit(limit).forEach(n ->
            sb.append("- ").append(n.name()).append("\n"));
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-runtime -Dtest=ConsolidationCapabilityTest -Dsurefire.useFile=false`
Expected: 11 tests PASS

- [ ] **Step 5: Commit**

```bash
git add cognitive-runtime/src/main/java/io/casehub/neocortex/cognitive/runtime/ConsolidationCapability.java \
      cognitive-runtime/src/test/java/io/casehub/neocortex/cognitive/runtime/ConsolidationCapabilityTest.java
git commit -m "feat(cognitive-runtime): add ConsolidationCapability — graduated/summary/curiosity rendering Refs #1237"
```

## Batch 3: Producer wiring + composition tests

### Task 5: CognitiveAvatarProducer wiring

**Files:**
- Modify: `cognitive-runtime/src/main/java/io/casehub/neocortex/cognitive/runtime/CognitiveAvatarProducer.java`
- Modify: `cognitive-runtime/src/test/java/io/casehub/neocortex/cognitive/runtime/CognitiveAvatarProducerTest.java`

**Interfaces:**
- Consumes: `InnerThoughtsCapability`, `AppraisalCapability`, `NeedsCapability`, `ConsolidationCapability` from Tasks 1-4
- Produces: Updated `CognitiveAvatarProducer.buildAndInitAvatar()` that includes all 4 new capabilities

- [ ] **Step 1: Write failing tests for producer wiring**

Add to `CognitiveAvatarProducerTest`:

```java
@Test
void createsAvatarWithNewCapabilities_whenStoresAvailable() {
    var mindMap = new InMemoryMindMapStore();
    var memory = new InMemoryMemoryStore(PRINCIPAL);
    var profile = new CognitiveProfile(mindMap, memory);
    var briefing = new BriefingConfig("identity", "grounding", "echo");
    var defaults = CognitiveDefaults.empty("mentor").withBriefing(briefing);
    var registry = CognitiveDefaultsRegistry.forTesting(defaults);

    var producer = new CognitiveAvatarProducer(
        registry,
        resolvedInstance(mindMap),
        resolvedInstance(memory),
        resolvedInstance(profile));

    var avatar = producer.create("mentor", TENANT);
    assertTrue(avatar.capability(InnerThoughtsCapability.class).isPresent());
    assertTrue(avatar.capability(AppraisalCapability.class).isPresent());
    assertTrue(avatar.capability(NeedsCapability.class).isPresent());
    assertTrue(avatar.capability(ConsolidationCapability.class).isPresent());
}

@Test
void newCapabilities_notCreated_whenStoresUnavailable() {
    var briefing = new BriefingConfig("identity", null, null);
    var defaults = CognitiveDefaults.empty("minimal").withBriefing(briefing);
    var registry = CognitiveDefaultsRegistry.forTesting(defaults);

    var producer = new CognitiveAvatarProducer(
        registry, unresolvedInstance(), unresolvedInstance(), unresolvedInstance());

    var avatar = producer.create("minimal", TENANT);
    assertFalse(avatar.capability(InnerThoughtsCapability.class).isPresent());
    assertFalse(avatar.capability(AppraisalCapability.class).isPresent());
    assertFalse(avatar.capability(NeedsCapability.class).isPresent());
    assertFalse(avatar.capability(ConsolidationCapability.class).isPresent());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-runtime -Dtest=CognitiveAvatarProducerTest -Dsurefire.useFile=false`
Expected: `createsAvatarWithNewCapabilities_whenStoresAvailable` FAIL — new capabilities not found

- [ ] **Step 3: Update CognitiveAvatarProducer.buildAndInitAvatar()**

Add to `buildAndInitAvatar()` after the existing capability additions, before `avatar.initialize()`:

```java
boolean cognitionActive = false; // TODO: detect via marker bean when cognition wiring is added

if (mindMapStore.isResolvable()) {
    capabilities.add(new NeedsCapability(mindMapStore.get(), cognitionActive));
    capabilities.add(new ConsolidationCapability(mindMapStore.get(), cognitionActive));
}
if (memoryStore.isResolvable()) {
    capabilities.add(new AppraisalCapability(memoryStore.get(), cognitionActive));
    capabilities.add(new InnerThoughtsCapability(memoryStore.get(), cognitionActive));
}
```

Note: `cognitionActive` detection (D5) will use `false` for now. The cognition module marker bean integration is a follow-up — the boolean constructor parameter is in place, and flipping it is a one-line change when the detection mechanism is wired.

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-runtime -Dtest=CognitiveAvatarProducerTest -Dsurefire.useFile=false`
Expected: All tests PASS (existing + 2 new)

- [ ] **Step 5: Update CapabilityCompositionTest for 10 capabilities**

Update `CapabilityCompositionTest.buildFullAvatar()` to include the 4 new capabilities. Update `allSixCapabilities_promptSectionsOrderedByBlockTag` to expect 10 sections. Update `allCapabilitiesEnabled_emptyStores_storeBackedCapabilitiesReturnNull` to expect exactly 2 blocks (briefing + disposition — the 4 new capabilities also return null with empty stores).

- [ ] **Step 6: Run full module test suite**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-runtime -Dsurefire.useFile=false`
Expected: All tests PASS

- [ ] **Step 7: Commit**

```bash
git add cognitive-runtime/src/main/java/io/casehub/neocortex/cognitive/runtime/CognitiveAvatarProducer.java \
      cognitive-runtime/src/test/java/io/casehub/neocortex/cognitive/runtime/CognitiveAvatarProducerTest.java \
      cognitive-runtime/src/test/java/io/casehub/neocortex/cognitive/runtime/CapabilityCompositionTest.java
git commit -m "feat(cognitive-runtime): wire 4 new capabilities into CognitiveAvatarProducer Refs #1237"
```

## References

- [2026-11-25-additional-capabilities-design.md] — design spec this plan implements
- [CognitiveCapability.java] — SPI interface
- [CognitiveAvatar.java] — composition root
- [CognitiveAvatarProducer.java] — CDI producer
- [MemoryCapability.java] — reference pattern for store-backed capabilities
- [GoalCapability.java] — reference pattern for MindMap-backed capabilities
- [BlockTag.java] — prompt section ordering
- [MoodAttributeKeys.java] — PAD attribute key constants
- [MemoryScanRequest.java] — scan API shape
- [decisions.md] — D1-D6 design decisions
- [GitHub #1237] — focal issue
- [GitHub #1238] — CapabilityCompositionTest (extended in Task 5)
