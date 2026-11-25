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
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryScanRequest;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class AppraisalCapability implements CognitiveCapability {

    private final CaseMemoryStore memoryStore;
    private final boolean cognitionActive;
    private String tenantId;

    public AppraisalCapability(CaseMemoryStore memoryStore, boolean cognitionActive) {
        this.memoryStore = memoryStore;
        this.cognitionActive = cognitionActive;
    }

    @Override
    public String name() { return "appraisal"; }

    @Override
    public void initialize(CognitiveAvatarContext context) {
        this.tenantId = context.tenantId();
    }

    @Override
    public boolean isActive() { return !cognitionActive; }

    @Override
    public List<CognitionPromptRenderer> promptSections() {
        return List.of(new AppraisalPromptSection());
    }

    private class AppraisalPromptSection implements CognitionPromptRenderer {

        @Override
        public @Nullable String render(CognitionRenderContext ctx) {
            if (tenantId == null) return null;
            var request = new MemoryScanRequest(tenantId, "mood", null, null, 1, null);
            var memories = memoryStore.scan(request);
            if (memories.isEmpty()) return null;

            var mood = memories.getFirst();
            if (mood.pleasure() == null && mood.arousal() == null && mood.dominance() == null) {
                return null;
            }

            var sb = new StringBuilder("Emotional state:\n");
            describePad(sb, mood);
            return sb.toString().strip();
        }

        @Override
        public BlockTag blockTag() { return BlockTag.APPRAISAL; }

        @Override
        public @Nullable SectionTier sectionTier() { return SectionTier.CONTEXTUAL; }
    }

    static void describePad(StringBuilder sb, Memory mood) {
        Double p = mood.pleasure();
        Double a = mood.arousal();
        Double d = mood.dominance();

        if (p != null) {
            sb.append("- Valence: ").append(p > 0.3 ? "positive" : p < -0.3 ? "negative" : "neutral").append("\n");
        }
        if (a != null) {
            sb.append("- Arousal: ").append(a > 0.3 ? "elevated" : a < -0.3 ? "low" : "moderate").append("\n");
        }
        if (d != null) {
            sb.append("- Sense of control: ").append(d > 0.3 ? "strong" : d < -0.3 ? "weak" : "balanced").append("\n");
        }
    }
}
