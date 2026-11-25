/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    void rendersMultipleTiers() {
        mindMapStore.addNode(NodeInput.of("safety-need", SubgraphTypes.COGNITIVE)
            .withProperties(Map.of(
                "cognitiveKind", "need-satisfaction",
                "tier", "SAFETY",
                "satisfaction", "0.8",
                "agent-id", "agent-1")), TENANT);
        mindMapStore.addNode(NodeInput.of("social-need", SubgraphTypes.COGNITIVE)
            .withProperties(Map.of(
                "cognitiveKind", "need-satisfaction",
                "tier", "SOCIAL",
                "satisfaction", "0.2",
                "agent-id", "agent-1")), TENANT);

        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNotNull(content);
        assertTrue(content.toLowerCase().contains("safety"));
        assertTrue(content.toLowerCase().contains("social"));
        assertTrue(content.contains("well met"));
        assertTrue(content.contains("unmet"));
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
