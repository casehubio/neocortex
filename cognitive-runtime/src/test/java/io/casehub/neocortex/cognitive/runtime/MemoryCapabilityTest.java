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
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MemoryCapabilityTest {

    private InMemoryMemoryStore store;
    private MemoryCapability capability;
    private static final String TENANT = "test-tenant";
    private static final String AGENT = "agent-1";

    @BeforeEach
    void setUp() {
        store = new InMemoryMemoryStore(new CurrentPrincipal() {
            @Override public String actorId() { return "test-actor"; }
            @Override public Set<String> groups() { return Set.of(); }
            @Override public String tenancyId() { return TENANT; }
            @Override public boolean isCrossTenantAdmin() { return false; }
        });
        capability = new MemoryCapability(store);
        capability.initialize(new CognitiveAvatarContext(
            AGENT, TENANT, CognitiveDefaults.empty(AGENT)));
    }

    @Test
    void rendersRecentMemories() {
        store.store(MemoryInput.of(Subject.of("person", "user-1"), new MemoryDomain("experience"), TENANT,
            "Had a productive meeting about the roadmap"));
        store.store(MemoryInput.of(Subject.of("person", "user-1"), new MemoryDomain("experience"), TENANT,
            "Completed the API review"));

        var ctx = new CognitionRenderContext(AGENT, TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNotNull(content);
        assertTrue(content.contains("Recent context:"));
        assertTrue(content.contains("Had a productive meeting about the roadmap"));
        assertTrue(content.contains("Completed the API review"));
    }

    @Test
    void returnsNullWhenNoMemories() {
        var ctx = new CognitionRenderContext(AGENT, TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNull(content);
    }

    @Test
    void returnsNullBeforeInitialization() {
        var uninitialised = new MemoryCapability(store);
        var ctx = new CognitionRenderContext(AGENT, TENANT, null);
        var content = uninitialised.promptSections().getFirst().render(ctx);
        assertNull(content);
    }

    @Test
    void filtersToExperienceDomain() {
        store.store(MemoryInput.of(Subject.of("person", "user-1"), new MemoryDomain("experience"), TENANT,
            "An experience memory"));
        store.store(MemoryInput.of(Subject.of("person", "user-1"), new MemoryDomain("reflection"), TENANT,
            "A reflection memory"));

        var ctx = new CognitionRenderContext(AGENT, TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNotNull(content);
        assertTrue(content.contains("An experience memory"));
        assertFalse(content.contains("A reflection memory"));
    }

    @Test
    void nameIsMemory() {
        assertEquals("memory", capability.name());
    }

    @Test
    void blockTagIsMemory() {
        assertEquals(BlockTag.MEMORY, capability.promptSections().getFirst().blockTag());
    }

    @Test
    void sectionTierIsSupplementary() {
        assertEquals(SectionTier.SUPPLEMENTARY, capability.promptSections().getFirst().sectionTier());
    }
}
