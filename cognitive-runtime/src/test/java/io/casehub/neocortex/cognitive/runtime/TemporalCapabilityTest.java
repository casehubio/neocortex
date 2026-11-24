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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class TemporalCapabilityTest {

    private InMemoryMindMapStore store;
    private TemporalCapability capability;
    private static final String TENANT = "test-tenant";

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        capability = new TemporalCapability(store);
        capability.initialize(new CognitiveAvatarContext(
            "agent-1", TENANT, CognitiveDefaults.empty("agent-1")));
    }

    @Test
    void rendersTemporalEntries() {
        var input = NodeInput.of("Project deadline", SubgraphTypes.GOAL)
            .withValidFrom(Instant.now().minus(1, ChronoUnit.DAYS));
        store.addNode(input, TENANT);

        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNotNull(content);
        assertTrue(content.contains("Temporal awareness:"));
    }

    @Test
    void returnsNullWhenNoTemporalEntries() {
        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNull(content);
    }

    @Test
    void skipsNodesWithoutValidFrom() {
        var input = NodeInput.of("No temporal data", SubgraphTypes.GOAL);
        store.addNode(input, TENANT);

        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNull(content);
    }

    @Test
    void blockTagIsTemporal() {
        assertEquals(BlockTag.TEMPORAL, capability.promptSections().getFirst().blockTag());
    }

    @Test
    void sectionTierIsContextual() {
        assertEquals(SectionTier.CONTEXTUAL, capability.promptSections().getFirst().sectionTier());
    }
}
