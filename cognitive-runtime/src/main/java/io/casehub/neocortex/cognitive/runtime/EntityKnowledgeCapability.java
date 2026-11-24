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

import io.casehub.neocortex.cognitive.index.CognitiveProfile;
import io.casehub.neocortex.cognition.prompt.BlockTag;
import io.casehub.neocortex.cognition.prompt.CognitionPromptRenderer;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.SectionTier;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class EntityKnowledgeCapability implements CognitiveCapability {

    private final CognitiveProfile cognitiveProfile;

    public EntityKnowledgeCapability(CognitiveProfile cognitiveProfile) {
        this.cognitiveProfile = cognitiveProfile;
    }

    @Override
    public String name() { return "entity-knowledge"; }

    @Override
    public List<CognitionPromptRenderer> promptSections() {
        return List.of(new EntityKnowledgePromptSection());
    }

    private class EntityKnowledgePromptSection implements CognitionPromptRenderer {

        @Override
        public @Nullable String render(CognitionRenderContext ctx) {
            if (ctx.subjectId() == null) return null;
            try {
                var query = io.casehub.neocortex.cognitive.index.CognitiveProfileQuery.byName(
                    ctx.subjectId(), ctx.tenantId());
                var knowledge = cognitiveProfile.resolve(query);
                if (knowledge.isEmpty()) return null;
                var ek = knowledge.get();

                var sb = new StringBuilder("About " + ctx.subjectId() + ":\n");
                if (ek.node() != null) {
                    sb.append("Known as: ").append(ek.node().name()).append("\n");
                    ek.node().property("description").ifPresent(d ->
                        sb.append("Description: ").append(d).append("\n"));
                }
                if (ek.memories() != null && !ek.memories().isEmpty()) {
                    sb.append("Relevant memories:\n");
                    ek.memories().values().stream()
                        .flatMap(java.util.Collection::stream)
                        .limit(5)
                        .forEach(m -> sb.append("- ").append(m.text()).append("\n"));
                }
                var result = sb.toString().strip();
                return result.length() > ("About " + ctx.subjectId() + ":").length()
                    ? result : null;
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public BlockTag blockTag() { return BlockTag.BELIEFS; }

        @Override
        public @Nullable SectionTier sectionTier() { return SectionTier.CONTEXTUAL; }
    }
}
