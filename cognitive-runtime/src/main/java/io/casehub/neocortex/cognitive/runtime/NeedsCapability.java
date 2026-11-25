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
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class NeedsCapability implements CognitiveCapability {

    private final MindMapStore mindMapStore;
    private final boolean cognitionActive;
    private String tenantId;
    private String agentId;

    public NeedsCapability(MindMapStore mindMapStore, boolean cognitionActive) {
        this.mindMapStore = mindMapStore;
        this.cognitionActive = cognitionActive;
    }

    @Override
    public String name() { return "needs"; }

    @Override
    public void initialize(CognitiveAvatarContext context) {
        this.tenantId = context.tenantId();
        this.agentId = context.agentId();
    }

    @Override
    public boolean isActive() { return !cognitionActive; }

    @Override
    public List<CognitionPromptRenderer> promptSections() {
        return List.of(new NeedsPromptSection());
    }

    private class NeedsPromptSection implements CognitionPromptRenderer {

        @Override
        public @Nullable String render(CognitionRenderContext ctx) {
            if (tenantId == null) return null;
            var nodes = mindMapStore.nodesIn(SubgraphTypes.COGNITIVE, tenantId);
            var needNodes = nodes.stream()
                .filter(n -> "need-satisfaction".equals(n.property("cognitiveKind").orElse(null)))
                .filter(n -> agentId == null || agentId.equals(n.property("agent-id").orElse(null)))
                .toList();
            if (needNodes.isEmpty()) return null;

            var sb = new StringBuilder("Current needs:\n");
            for (var node : needNodes) {
                var tier = node.property("tier").orElse("unknown");
                var satisfaction = node.property("satisfaction").orElse("unknown");
                sb.append("- ").append(tier.toLowerCase().replace('_', ' '))
                    .append(": ").append(describeSatisfaction(satisfaction)).append("\n");
            }
            return sb.toString().strip();
        }

        @Override
        public BlockTag blockTag() { return BlockTag.NEEDS; }

        @Override
        public @Nullable SectionTier sectionTier() { return SectionTier.CONTEXTUAL; }
    }

    private static String describeSatisfaction(String value) {
        try {
            double v = Double.parseDouble(value);
            if (v >= 0.7) return "well met";
            if (v >= 0.4) return "partially met";
            return "unmet";
        } catch (NumberFormatException e) {
            return value;
        }
    }
}
