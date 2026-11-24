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
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryScanRequest;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class MemoryCapability implements CognitiveCapability {

    private final CaseMemoryStore memoryStore;
    private String tenantId;

    public MemoryCapability(CaseMemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    @Override
    public String name() { return "memory"; }

    @Override
    public void initialize(CognitiveAvatarContext context) {
        this.tenantId = context.tenantId();
    }

    @Override
    public List<CognitionPromptRenderer> promptSections() {
        return List.of(new MemoryPromptSection());
    }

    private class MemoryPromptSection implements CognitionPromptRenderer {

        @Override
        public @Nullable String render(CognitionRenderContext ctx) {
            if (tenantId == null) return null;
            var request = new MemoryScanRequest(tenantId, "experience", null, null, 10, null);
            var memories = memoryStore.scan(request);
            if (memories.isEmpty()) return null;

            var sb = new StringBuilder("Recent context:\n");
            for (var memory : memories) {
                sb.append("- ").append(memory.text()).append("\n");
            }
            return sb.toString().strip();
        }

        @Override
        public BlockTag blockTag() { return BlockTag.MEMORY; }

        @Override
        public @Nullable SectionTier sectionTier() { return SectionTier.SUPPLEMENTARY; }
    }
}
