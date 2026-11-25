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
import io.casehub.neocortex.cognitive.index.CognitiveProfile;
import io.casehub.neocortex.cognition.prompt.BlockTag;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.SectionTier;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EntityKnowledgeCapabilityTest {

    private InMemoryMindMapStore mindMapStore;
    private InMemoryMemoryStore memoryStore;
    private EntityKnowledgeCapability capability;
    private static final String TENANT = "test-tenant";
    private static final String AGENT = "agent-1";

    @BeforeEach
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        memoryStore = new InMemoryMemoryStore(new CurrentPrincipal() {
            @Override public String actorId() { return "test-actor"; }
            @Override public Set<String> groups() { return Set.of(); }
            @Override public String tenancyId() { return TENANT; }
            @Override public boolean isCrossTenantAdmin() { return false; }
        });
        var profile = new CognitiveProfile(mindMapStore, memoryStore);
        capability = new EntityKnowledgeCapability(profile);
    }

    @Test
    void rendersKnownEntity() {
        var input = NodeInput.of("Alice", SubgraphTypes.PERSON)
            .withProperties(Map.of("description", "A software engineer working on the platform"));
        mindMapStore.addNode(input, TENANT);

        var ctx = new CognitionRenderContext(AGENT, TENANT, "Alice");
        var content = capability.promptSections().getFirst().render(ctx);
        assertNotNull(content);
        assertTrue(content.contains("About Alice:"));
        assertTrue(content.contains("Known as: Alice"));
        assertTrue(content.contains("A software engineer working on the platform"));
    }

    @Test
    void returnsNullForUnknownEntity() {
        var ctx = new CognitionRenderContext(AGENT, TENANT, "Nobody");
        var content = capability.promptSections().getFirst().render(ctx);
        assertNull(content);
    }

    @Test
    void returnsNullWhenSubjectIdIsNull() {
        var ctx = new CognitionRenderContext(AGENT, TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNull(content);
    }

    @Test
    void includesRelevantMemories() {
        var input = NodeInput.of("Bob", SubgraphTypes.PERSON);
        mindMapStore.addNode(input, TENANT);

        memoryStore.store(MemoryInput.of(Subject.of("person", "Bob"), new MemoryDomain("experience"), TENANT,
            "Bob presented the quarterly results"));

        var ctx = new CognitionRenderContext(AGENT, TENANT, "Bob");
        var content = capability.promptSections().getFirst().render(ctx);
        assertNotNull(content);
        assertTrue(content.contains("About Bob:"));
        assertTrue(content.contains("Relevant memories:"));
        assertTrue(content.contains("Bob presented the quarterly results"));
    }

    @Test
    void rendersNodeWithoutDescription() {
        var input = NodeInput.of("Charlie", SubgraphTypes.PERSON);
        mindMapStore.addNode(input, TENANT);

        memoryStore.store(MemoryInput.of(Subject.of("person", "Charlie"), new MemoryDomain("experience"), TENANT,
            "Met Charlie at the conference"));

        var ctx = new CognitionRenderContext(AGENT, TENANT, "Charlie");
        var content = capability.promptSections().getFirst().render(ctx);
        assertNotNull(content);
        assertTrue(content.contains("Known as: Charlie"));
        assertFalse(content.contains("Description:"));
    }

    @Test
    void nameIsEntityKnowledge() {
        assertEquals("entity-knowledge", capability.name());
    }

    @Test
    void blockTagIsBeliefs() {
        assertEquals(BlockTag.BELIEFS, capability.promptSections().getFirst().blockTag());
    }

    @Test
    void sectionTierIsContextual() {
        assertEquals(SectionTier.CONTEXTUAL, capability.promptSections().getFirst().sectionTier());
    }
}
