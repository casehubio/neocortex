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

import io.casehub.neocortex.cognitive.index.AttentionItem;
import io.casehub.neocortex.cognitive.index.TemporalEntry;
import io.casehub.neocortex.cognitive.index.TemporalFocus;
import io.casehub.neocortex.cognitive.index.TemporalFocusConfig;
import io.casehub.neocortex.cognitive.index.TemporalSource;
import io.casehub.neocortex.cognition.prompt.BlockTag;
import io.casehub.neocortex.cognition.prompt.CognitionPromptRenderer;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.SectionTier;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TemporalCapability implements CognitiveCapability {

    private final MindMapStore mindMapStore;
    private String tenantId;
    private TemporalFocusConfig config;

    public TemporalCapability(MindMapStore mindMapStore) {
        this.mindMapStore = mindMapStore;
    }

    @Override
    public String name() { return "temporal"; }

    @Override
    public void initialize(CognitiveAvatarContext context) {
        this.tenantId = context.tenantId();
        this.config = context.profile().temporalFocus() != null
            ? context.profile().temporalFocus()
            : TemporalFocusConfig.defaults();
    }

    @Override
    public List<CognitionPromptRenderer> promptSections() {
        return List.of(new TemporalPromptSection());
    }

    private class TemporalPromptSection implements CognitionPromptRenderer {

        @Override
        public @Nullable String render(CognitionRenderContext ctx) {
            if (tenantId == null) return null;
            var entries = buildEntries();
            if (entries.isEmpty()) return null;

            var items = TemporalFocus.focus(entries, Instant.now(), Map.of(), config);
            if (items.isEmpty()) return null;

            var sb = new StringBuilder("Temporal awareness:\n");
            items.stream().limit(5).forEach(item ->
                sb.append("- ").append(item.reason()).append("\n"));
            return sb.toString().strip();
        }

        @Override
        public BlockTag blockTag() { return BlockTag.TEMPORAL; }

        @Override
        public @Nullable SectionTier sectionTier() { return SectionTier.CONTEXTUAL; }
    }

    private List<TemporalEntry> buildEntries() {
        var entries = new ArrayList<TemporalEntry>();
        for (var subgraph : List.of(SubgraphTypes.GOAL, SubgraphTypes.PERSON,
                SubgraphTypes.PROJECT, SubgraphTypes.ACTIVITY)) {
            try {
                for (MindMapNode node : mindMapStore.nodesIn(subgraph, tenantId)) {
                    if (node.validFrom() != null) {
                        entries.add(new TemporalEntry(
                            node.validFrom(),
                            new TemporalSource.FromMindMap(node),
                            tenantId,
                            node.confidence()));
                    }
                }
            } catch (Exception e) {
                // skip — subgraph may not exist
            }
        }
        return entries;
    }
}
