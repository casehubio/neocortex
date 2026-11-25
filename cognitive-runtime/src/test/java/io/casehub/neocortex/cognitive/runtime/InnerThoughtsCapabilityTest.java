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
