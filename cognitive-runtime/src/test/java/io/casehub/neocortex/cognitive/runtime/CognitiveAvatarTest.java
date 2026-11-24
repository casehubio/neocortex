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

import io.casehub.neocortex.cognitive.index.BriefingConfig;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognition.prompt.CognitionPromptRenderer;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.CognitivePromptSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CognitiveAvatarTest {

    @Test
    void implementsCognitivePromptSource() {
        var profile = CognitiveDefaults.empty("test");
        var avatar = new CognitiveAvatar(profile, List.of());
        assertInstanceOf(CognitivePromptSource.class, avatar);
    }

    @Test
    void composesCapabilityRenderers() {
        var config = new BriefingConfig("You are a test.", null, "Think as a test.");
        var profile = CognitiveDefaults.empty("test").withBriefing(config);
        var capability = new BriefingCapability(config);
        var avatar = new CognitiveAvatar(profile, List.of(capability));

        var sections = avatar.promptSections();
        assertFalse(sections.isEmpty());

        var ctx = new CognitionRenderContext("test", "tenant", null);
        var content = sections.getFirst().render(ctx);
        assertTrue(content.contains("You are a test."));
    }

    @Test
    void skipsInactiveCapabilities() {
        var inactive = new CognitiveCapability() {
            @Override public String name() { return "inactive"; }
            @Override public List<CognitionPromptRenderer> promptSections() {
                return List.of();
            }
            @Override public boolean isActive() { return false; }
        };
        var profile = CognitiveDefaults.empty("test");
        var avatar = new CognitiveAvatar(profile, List.of(inactive));
        assertTrue(avatar.promptSections().isEmpty());
    }

    @Test
    void capabilityLookupByType() {
        var config = new BriefingConfig("id", null, null);
        var briefing = new BriefingCapability(config);
        var profile = CognitiveDefaults.empty("test");
        var avatar = new CognitiveAvatar(profile, List.of(briefing));

        assertTrue(avatar.capability(BriefingCapability.class).isPresent());
    }

    @Test
    void contributeBlocksProducesPromptBlocks() {
        var config = new BriefingConfig("You are a test.", null, null);
        var profile = CognitiveDefaults.empty("test").withBriefing(config);
        var avatar = new CognitiveAvatar(profile, List.of(new BriefingCapability(config)));

        var ctx = new CognitionRenderContext("test", "tenant", null);
        var blocks = avatar.contributeBlocks(ctx);
        assertEquals(1, blocks.size());
        assertEquals("PERSONALITY", blocks.getFirst().tag());
        assertEquals(1.0f, blocks.getFirst().salience());
    }

    @Test
    void emptyCapabilitiesProduceEmptyBlocks() {
        var profile = CognitiveDefaults.empty("test");
        var avatar = new CognitiveAvatar(profile, List.of());

        var ctx = new CognitionRenderContext("test", "tenant", null);
        var blocks = avatar.contributeBlocks(ctx);
        assertTrue(blocks.isEmpty());
    }

    @Test
    void profileAccessor() {
        var profile = CognitiveDefaults.empty("test-agent");
        var avatar = new CognitiveAvatar(profile, List.of());
        assertEquals("test-agent", avatar.profile().agentId());
    }

    @Test
    void initializeCascadesToCapabilities() {
        var initialized = new boolean[]{false};
        var cap = new CognitiveCapability() {
            @Override public String name() { return "tracker"; }
            @Override public void initialize(CognitiveAvatarContext ctx) {
                initialized[0] = true;
                assertEquals("agent-1", ctx.agentId());
                assertEquals("tenant-1", ctx.tenantId());
            }
            @Override public List<CognitionPromptRenderer> promptSections() {
                return List.of();
            }
        };
        var profile = CognitiveDefaults.empty("agent-1");
        var avatar = new CognitiveAvatar(profile, List.of(cap));
        avatar.initialize("agent-1", "tenant-1");
        assertTrue(initialized[0]);
    }
}
