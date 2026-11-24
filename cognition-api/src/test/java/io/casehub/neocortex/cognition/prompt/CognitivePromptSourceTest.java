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
package io.casehub.neocortex.cognition.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CognitivePromptSourceTest {

    @Test
    void contributeBlocks_renders_active_sections() {
        var renderer = new CognitionPromptRenderer() {
            @Override public String render(CognitionRenderContext ctx) { return "test content"; }
            @Override public BlockTag blockTag() { return BlockTag.PERSONALITY; }
            @Override public SectionTier sectionTier() { return SectionTier.CORE; }
        };

        CognitivePromptSource source = new CognitivePromptSource() {
            @Override public List<CognitionPromptRenderer> promptSections() {
                return List.of(renderer);
            }
        };

        var ctx = new CognitionRenderContext("agent-1", "tenant-1", null);
        var blocks = source.contributeBlocks(ctx);
        assertEquals(1, blocks.size());
        assertEquals("test content", blocks.getFirst().content());
        assertEquals(1.0f, blocks.getFirst().salience());
    }

    @Test
    void contributeBlocks_skips_null_content() {
        var renderer = new CognitionPromptRenderer() {
            @Override public String render(CognitionRenderContext ctx) { return null; }
            @Override public BlockTag blockTag() { return BlockTag.PERSONALITY; }
        };

        CognitivePromptSource source = new CognitivePromptSource() {
            @Override public List<CognitionPromptRenderer> promptSections() {
                return List.of(renderer);
            }
        };

        var blocks = source.contributeBlocks(new CognitionRenderContext("a", "t", null));
        assertTrue(blocks.isEmpty());
    }

    @Test
    void contributeBlocks_skips_blank_content() {
        var renderer = new CognitionPromptRenderer() {
            @Override public String render(CognitionRenderContext ctx) { return "   "; }
            @Override public BlockTag blockTag() { return BlockTag.PERSONALITY; }
        };

        CognitivePromptSource source = new CognitivePromptSource() {
            @Override public List<CognitionPromptRenderer> promptSections() {
                return List.of(renderer);
            }
        };

        var blocks = source.contributeBlocks(new CognitionRenderContext("a", "t", null));
        assertTrue(blocks.isEmpty());
    }

    @Test
    void cognitiveLifecycle_is_subtype() {
        assertTrue(CognitivePromptSource.class.isAssignableFrom(CognitiveLifecycle.class));
    }

    @Test
    void salience_maps_section_tiers() {
        CognitivePromptSource source = new CognitivePromptSource() {
            @Override public List<CognitionPromptRenderer> promptSections() {
                return List.of(
                    rendererWithTier(SectionTier.CORE),
                    rendererWithTier(SectionTier.CONTEXTUAL),
                    rendererWithTier(SectionTier.SUPPLEMENTARY)
                );
            }
        };

        var ctx = new CognitionRenderContext("a", "t", null);
        var blocks = source.contributeBlocks(ctx);
        assertEquals(3, blocks.size());
        assertEquals(1.0f, blocks.get(0).salience());
        assertEquals(0.6f, blocks.get(1).salience());
        assertEquals(0.3f, blocks.get(2).salience());
    }

    private static CognitionPromptRenderer rendererWithTier(SectionTier tier) {
        return new CognitionPromptRenderer() {
            @Override public String render(CognitionRenderContext ctx) { return "content"; }
            @Override public BlockTag blockTag() { return BlockTag.PERSONALITY; }
            @Override public SectionTier sectionTier() { return tier; }
        };
    }
}
