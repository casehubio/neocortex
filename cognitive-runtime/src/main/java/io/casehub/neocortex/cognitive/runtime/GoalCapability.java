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
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public class GoalCapability implements CognitiveCapability {

    private final MindMapStore mindMapStore;
    private String tenantId;

    public GoalCapability(MindMapStore mindMapStore) {
        this.mindMapStore = mindMapStore;
    }

    @Override
    public String name() { return "goals"; }

    @Override
    public void initialize(CognitiveAvatarContext context) {
        this.tenantId = context.tenantId();
    }

    @Override
    public List<CognitionPromptRenderer> promptSections() {
        return List.of(new GoalPromptSection());
    }

    private class GoalPromptSection implements CognitionPromptRenderer {

        @Override
        public @Nullable String render(CognitionRenderContext ctx) {
            if (tenantId == null) return null;
            var goals = mindMapStore.nodesIn(SubgraphTypes.GOAL, tenantId);
            var active = goals.stream()
                .filter(n -> !"abandoned".equals(n.property("status").orElse(null)))
                .filter(n -> !"dormant".equals(n.property("status").orElse(null)))
                .sorted(Comparator.comparingDouble(GoalCapability::priorityOf).reversed())
                .limit(10)
                .toList();
            if (active.isEmpty()) return null;

            var sb = new StringBuilder("Active goals:\n");
            for (var goal : active) {
                sb.append("- ").append(goal.name());
                goal.property("priority").ifPresent(p -> sb.append(" (priority: ").append(p).append(")"));
                goal.property("horizon").ifPresent(h -> sb.append(" [").append(h).append("]"));
                goal.property("description").ifPresent(d -> sb.append(": ").append(d));
                sb.append("\n");
            }
            return sb.toString().strip();
        }

        @Override
        public BlockTag blockTag() { return BlockTag.GOALS; }

        @Override
        public @Nullable SectionTier sectionTier() { return SectionTier.CONTEXTUAL; }
    }

    static double priorityOf(MindMapNode node) {
        return node.property("priority")
            .map(p -> { try { return Double.parseDouble(p); } catch (NumberFormatException e) { return 0.5; } })
            .orElse(0.5);
    }
}
