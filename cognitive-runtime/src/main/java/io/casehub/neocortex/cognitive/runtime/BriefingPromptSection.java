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

import io.casehub.neocortex.cognitive.index.BriefingConfig;
import io.casehub.neocortex.cognition.prompt.BlockTag;
import io.casehub.neocortex.cognition.prompt.CognitionPromptRenderer;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;
import io.casehub.neocortex.cognition.prompt.SectionTier;
import org.jspecify.annotations.Nullable;

public class BriefingPromptSection implements CognitionPromptRenderer {

    private final BriefingConfig briefing;

    public BriefingPromptSection(BriefingConfig briefing) {
        this.briefing = briefing;
    }

    @Override
    public @Nullable String render(CognitionRenderContext ctx) {
        if (briefing == null) return null;
        var sb = new StringBuilder();
        if (briefing.identity() != null) sb.append(briefing.identity()).append("\n\n");
        if (briefing.grounding() != null) sb.append(briefing.grounding()).append("\n\n");
        if (briefing.echo() != null) sb.append(briefing.echo());
        var result = sb.toString().strip();
        return result.isEmpty() ? null : result;
    }

    @Override
    public BlockTag blockTag() { return BlockTag.PERSONALITY; }

    @Override
    public @Nullable SectionTier sectionTier() { return SectionTier.CORE; }
}
