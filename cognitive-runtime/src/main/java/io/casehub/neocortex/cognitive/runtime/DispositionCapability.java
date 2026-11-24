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
import io.casehub.neocortex.cognition.prompt.BlockTag;
import io.casehub.neocortex.cognition.prompt.CognitionPromptRenderer;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.SectionTier;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class DispositionCapability implements CognitiveCapability {

    private final CognitiveDefaults profile;

    public DispositionCapability(CognitiveDefaults profile) {
        this.profile = profile;
    }

    @Override
    public String name() { return "disposition"; }

    @Override
    public List<CognitionPromptRenderer> promptSections() {
        return List.of(new DispositionPromptSection());
    }

    private class DispositionPromptSection implements CognitionPromptRenderer {

        @Override
        public @Nullable String render(CognitionRenderContext ctx) {
            DescriptorView descriptor = profile.descriptor();
            if (descriptor == null || descriptor.disposition() == null) return null;

            var axes = descriptor.disposition();
            var sb = new StringBuilder("Disposition:\n");
            appendIfPresent(sb, "Social orientation", axes.socialOrient());
            appendIfPresent(sb, "Rule following", axes.ruleFollowing());
            appendIfPresent(sb, "Risk appetite", axes.riskAppetite());
            appendIfPresent(sb, "Autonomy", axes.autonomy());
            appendIfPresent(sb, "Conflict mode", axes.conflictMode());

            var result = sb.toString().strip();
            return result.equals("Disposition:") ? null : result;
        }

        @Override
        public BlockTag blockTag() { return BlockTag.BEHAVIORAL; }

        @Override
        public @Nullable SectionTier sectionTier() { return SectionTier.CORE; }
    }

    private static void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("- ").append(label).append(": ").append(value).append("\n");
        }
    }
}
