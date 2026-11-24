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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.CognitivePromptSource;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CognitiveAvatarIntegrationTest {

    private static final String TENANT = "test-tenant";

    private static CognitiveDefaults loadProfile(String name) throws IOException {
        var mapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (var is = CognitiveAvatarIntegrationTest.class.getClassLoader()
                .getResourceAsStream("cognitive-profiles/" + name + ".yaml")) {
            assertNotNull(is);
            return mapper.readValue(is, CognitiveDefaults.class);
        }
    }

    @Test
    void endToEnd_briefingOnly() throws IOException {
        var profile = loadProfile("stoic-mentor");

        var capabilities = new ArrayList<CognitiveCapability>();
        capabilities.add(new BriefingCapability(profile.briefing()));

        var avatar = new CognitiveAvatar(profile, capabilities);
        avatar.initialize("stoic-mentor", TENANT);

        assertInstanceOf(CognitivePromptSource.class, avatar);

        var ctx = new CognitionRenderContext("stoic-mentor", TENANT, null);
        var blocks = avatar.contributeBlocks(ctx);
        assertEquals(1, blocks.size());
        assertTrue(blocks.getFirst().content().contains("stoic mentor"));
        assertEquals(1.0f, blocks.getFirst().salience());
    }

    @Test
    void endToEnd_briefingPlusGoals() throws IOException {
        var profile = loadProfile("stoic-mentor");
        var store = new InMemoryMindMapStore();
        store.addNode(NodeInput.of("Learn philosophy", SubgraphTypes.GOAL)
            .withProperties(Map.of("priority", "0.8")), TENANT);

        var capabilities = List.<CognitiveCapability>of(
            new BriefingCapability(profile.briefing()),
            new GoalCapability(store));
        var avatar = new CognitiveAvatar(profile, capabilities);
        avatar.initialize("stoic-mentor", TENANT);

        var ctx = new CognitionRenderContext("stoic-mentor", TENANT, null);
        var blocks = avatar.contributeBlocks(ctx);
        assertEquals(2, blocks.size());
        assertTrue(blocks.stream().anyMatch(b -> b.content().contains("stoic mentor")));
        assertTrue(blocks.stream().anyMatch(b -> b.content().contains("Learn philosophy")));
    }

    @Test
    void contrastingPersonas_produceDifferentOutput() throws IOException {
        var stoic = loadProfile("stoic-mentor");
        var bold = loadProfile("bold-strategist");

        var stoicAvatar = new CognitiveAvatar(stoic,
            List.of(new BriefingCapability(stoic.briefing())));
        var boldAvatar = new CognitiveAvatar(bold,
            List.of(new BriefingCapability(bold.briefing())));

        stoicAvatar.initialize("stoic-mentor", TENANT);
        boldAvatar.initialize("bold-strategist", TENANT);

        var ctx1 = new CognitionRenderContext("stoic-mentor", TENANT, null);
        var ctx2 = new CognitionRenderContext("bold-strategist", TENANT, null);

        var stoicContent = stoicAvatar.contributeBlocks(ctx1).getFirst().content();
        var boldContent = boldAvatar.contributeBlocks(ctx2).getFirst().content();

        assertTrue(stoicContent.contains("duty"));
        assertTrue(boldContent.contains("ambitious"));
        assertNotEquals(stoicContent, boldContent);
    }

    @Test
    void sortOrderMatchesBlockTagOrdinals() throws IOException {
        var profile = loadProfile("stoic-mentor");
        var store = new InMemoryMindMapStore();
        store.addNode(NodeInput.of("Goal A", SubgraphTypes.GOAL), TENANT);

        var capabilities = List.<CognitiveCapability>of(
            new GoalCapability(store),
            new BriefingCapability(profile.briefing()),
            new DispositionCapability(profile));

        var avatar = new CognitiveAvatar(profile, capabilities);
        avatar.initialize("stoic-mentor", TENANT);

        var sections = avatar.promptSections();
        assertTrue(sections.size() >= 2);
        int prevOrdinal = -1;
        for (var section : sections) {
            if (section.blockTag() != null) {
                assertTrue(section.blockTag().sortOrdinal() >= prevOrdinal,
                    "Sections should be ordered by BlockTag ordinal");
                prevOrdinal = section.blockTag().sortOrdinal();
            }
        }
    }
}
