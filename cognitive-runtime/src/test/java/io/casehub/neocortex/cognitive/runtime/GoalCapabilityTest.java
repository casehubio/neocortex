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

import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
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

class GoalCapabilityTest {

    private InMemoryMindMapStore store;
    private GoalCapability capability;
    private static final String TENANT = "test-tenant";

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        capability = new GoalCapability(store);
        capability.initialize(new CognitiveAvatarContext(
            "agent-1", TENANT, CognitiveDefaults.empty("agent-1")));
    }

    @Test
    void rendersActiveGoals() {
        var input = NodeInput.of("Learn Spanish", SubgraphTypes.GOAL);
        store.addNode(input, TENANT);

        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNotNull(content);
        assertTrue(content.contains("Learn Spanish"));
        assertTrue(content.contains("Active goals:"));
    }

    @Test
    void excludesAbandonedGoals() {
        var input = NodeInput.of("Old goal", SubgraphTypes.GOAL)
            .withProperties(Map.of("status", "abandoned"));
        store.addNode(input, TENANT);

        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNull(content);
    }

    @Test
    void excludesDormantGoals() {
        var input = NodeInput.of("Dormant goal", SubgraphTypes.GOAL)
            .withProperties(Map.of("status", "dormant"));
        store.addNode(input, TENANT);

        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNull(content);
    }

    @Test
    void rendersPriorityAndHorizon() {
        var input = NodeInput.of("Ship v2", SubgraphTypes.GOAL)
            .withProperties(Map.of("priority", "0.9", "horizon", "short"));
        store.addNode(input, TENANT);

        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertTrue(content.contains("priority: 0.9"));
        assertTrue(content.contains("[short]"));
    }

    @Test
    void returnsNullWhenNoGoals() {
        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNull(content);
    }

    @Test
    void blockTagIsGoals() {
        assertEquals(BlockTag.GOALS, capability.promptSections().getFirst().blockTag());
    }

    @Test
    void sectionTierIsContextual() {
        assertEquals(SectionTier.CONTEXTUAL, capability.promptSections().getFirst().sectionTier());
    }
}
