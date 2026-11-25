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
        assertTrue(content.contains("Emotional state"));
    }

    @Test
    void mapsPositivePleasureCorrectly() {
        memoryStore.store(MemoryInput.of(
            Subject.of("agent", "agent-1"), new MemoryDomain("mood"), TENANT, "mood")
            .withPad(0.6, 0.0, 0.0));

        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNotNull(content);
        assertTrue(content.contains("positive"));
    }

    @Test
    void mapsNegativePleasureCorrectly() {
        memoryStore.store(MemoryInput.of(
            Subject.of("agent", "agent-1"), new MemoryDomain("mood"), TENANT, "mood")
            .withPad(-0.5, 0.0, 0.0));

        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNotNull(content);
        assertTrue(content.contains("negative"));
    }

    @Test
    void returnsNullWhenNoMoodData() {
        var ctx = new CognitionRenderContext("agent-1", TENANT, null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNull(content);
    }

    @Test
    void returnsNullWhenPadValuesAllNull() {
        memoryStore.store(MemoryInput.of(
            Subject.of("agent", "agent-1"), new MemoryDomain("mood"), TENANT, "mood snapshot"));

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
