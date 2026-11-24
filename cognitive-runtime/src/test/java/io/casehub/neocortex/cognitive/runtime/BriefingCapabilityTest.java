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
import io.casehub.neocortex.cognition.prompt.BlockTag;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.SectionTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BriefingCapabilityTest {

    @Test
    void rendersThreeLayerBriefing() {
        var config = new BriefingConfig("You are stoic.", "Draw on memories.", "Think stoically.");
        var capability = new BriefingCapability(config);
        var sections = capability.promptSections();
        assertEquals(1, sections.size());

        var ctx = new CognitionRenderContext("agent", "tenant", null);
        var content = sections.getFirst().render(ctx);
        assertTrue(content.contains("You are stoic."));
        assertTrue(content.contains("Draw on memories."));
        assertTrue(content.contains("Think stoically."));
    }

    @Test
    void blockTagIsPersonality() {
        var config = new BriefingConfig("identity", null, null);
        var capability = new BriefingCapability(config);
        assertEquals(BlockTag.PERSONALITY, capability.promptSections().getFirst().blockTag());
    }

    @Test
    void sectionTierIsCore() {
        var config = new BriefingConfig("identity", null, null);
        var capability = new BriefingCapability(config);
        assertEquals(SectionTier.CORE, capability.promptSections().getFirst().sectionTier());
    }

    @Test
    void skipsNullLayers() {
        var config = new BriefingConfig("You are stoic.", null, "Think stoically.");
        var capability = new BriefingCapability(config);
        var ctx = new CognitionRenderContext("agent", "tenant", null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertTrue(content.contains("You are stoic."));
        assertFalse(content.contains("null"));
        assertTrue(content.contains("Think stoically."));
    }

    @Test
    void nameIsBriefing() {
        var config = new BriefingConfig("id", null, null);
        assertEquals("briefing", new BriefingCapability(config).name());
    }

    @Test
    void returnsNullForAllNullLayers() {
        var config = new BriefingConfig(null, null, null);
        var capability = new BriefingCapability(config);
        var ctx = new CognitionRenderContext("agent", "tenant", null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNull(content);
    }
}
