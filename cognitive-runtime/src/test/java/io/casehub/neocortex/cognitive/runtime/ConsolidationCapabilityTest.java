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
