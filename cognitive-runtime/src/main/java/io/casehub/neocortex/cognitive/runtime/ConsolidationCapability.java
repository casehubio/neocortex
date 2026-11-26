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
import io.casehub.neocortex.cognition.prompt.CognitionPromptRenderer;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.SectionTier;
import io.casehub.neocortex.mindmap.ConsolidationTraits;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ConsolidationCapability implements CognitiveCapability {

    private final MindMapStore mindMapStore;
    private final boolean cognitionActive;
    private String tenantId;

    public ConsolidationCapability(MindMapStore mindMapStore, boolean cognitionActive) {
        this.mindMapStore = mindMapStore;
        this.cognitionActive = cognitionActive;
    }

    @Override
    public String name() { return "consolidation"; }

    @Override
    public void initialize(CognitiveAvatarContext context) {
        this.tenantId = context.tenantId();
    }

    @Override
    public boolean isActive() { return !cognitionActive; }

    @Override
    public List<CognitionPromptRenderer> promptSections() {
        return List.of(new ConsolidationPromptSection());
    }

    private class ConsolidationPromptSection implements CognitionPromptRenderer {

        @Override
        public @Nullable String render(CognitionRenderContext ctx) {
            if (tenantId == null) return null;
            var allNodes = mindMapStore.nodesIn(SubgraphTypes.COGNITIVE, tenantId);

            var graduated = new ArrayList<MindMapNode>();
            var summaries = new ArrayList<MindMapNode>();
            var curiosity = new ArrayList<MindMapNode>();

            for (var node : allNodes) {
                var traits = node.traits();
                if (traits.contains(ConsolidationTraits.GRADUATED_SUB_THOUGHT)) graduated.add(node);
                else if (traits.contains(ConsolidationTraits.SUMMARY)) summaries.add(node);
                else if (traits.contains(ConsolidationTraits.CURIOSITY_DESCRIPTION_GENERATED)) curiosity.add(node);
            }

            if (graduated.isEmpty() && summaries.isEmpty() && curiosity.isEmpty()) {
                return null;
            }

            var sb = new StringBuilder("Recent learning:\n");
            appendSection(sb, "Graduated insights", graduated, 5);
            appendSection(sb, "Summaries", summaries, 3);
            appendSection(sb, "Curiosity", curiosity, 3);
            return sb.toString().strip();
        }

        @Override
        public BlockTag blockTag() { return BlockTag.CONSOLIDATION; }

        @Override
        public @Nullable SectionTier sectionTier() { return SectionTier.SUPPLEMENTARY; }
    }

    private static void appendSection(StringBuilder sb, String header,
                                       List<MindMapNode> nodes, int limit) {
        if (nodes.isEmpty()) return;
        sb.append(header).append(":\n");
        nodes.stream().limit(limit).forEach(n ->
            sb.append("- ").append(n.name()).append("\n"));
    }
}
