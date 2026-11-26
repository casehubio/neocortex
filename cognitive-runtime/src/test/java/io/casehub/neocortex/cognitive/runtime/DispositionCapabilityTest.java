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
import io.casehub.neocortex.cognitive.index.DescriptorView;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.cognition.prompt.BlockTag;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.SectionTier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DispositionCapabilityTest {

    @Test
    void rendersDispositionAxes() {
        var axes = new DispositionAxes("collaborative", "strict", "conservative", "autonomous", "collaborating");
        var descriptor = DescriptorView.of("agent-1", axes, List.of(), List.of());
        var profile = CognitiveDefaults.empty("agent-1").withDescriptor(descriptor);
        var capability = new DispositionCapability(profile);

        var ctx = new CognitionRenderContext("agent-1", "tenant", null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNotNull(content);
        assertTrue(content.contains("Social orientation: collaborative"));
        assertTrue(content.contains("Rule following: strict"));
        assertTrue(content.contains("Risk appetite: conservative"));
    }

    @Test
    void returnsNullWithoutDescriptor() {
        var profile = CognitiveDefaults.empty("agent-1");
        var capability = new DispositionCapability(profile);

        var ctx = new CognitionRenderContext("agent-1", "tenant", null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNull(content);
    }

    @Test
    void returnsNullWithNullAxes() {
        var descriptor = DescriptorView.of("agent-1", null, List.of(), List.of());
        var profile = CognitiveDefaults.empty("agent-1").withDescriptor(descriptor);
        var capability = new DispositionCapability(profile);

        var ctx = new CognitionRenderContext("agent-1", "tenant", null);
        var content = capability.promptSections().getFirst().render(ctx);
        assertNull(content);
    }

    @Test
    void blockTagIsBehavioral() {
        var profile = CognitiveDefaults.empty("agent-1");
        var capability = new DispositionCapability(profile);
        assertEquals(BlockTag.BEHAVIORAL, capability.promptSections().getFirst().blockTag());
    }

    @Test
    void sectionTierIsCore() {
        var profile = CognitiveDefaults.empty("agent-1");
        var capability = new DispositionCapability(profile);
        assertEquals(SectionTier.CORE, capability.promptSections().getFirst().sectionTier());
    }

    @Test
    void nameIsDisposition() {
        var profile = CognitiveDefaults.empty("agent-1");
        assertEquals("disposition", new DispositionCapability(profile).name());
    }
}
