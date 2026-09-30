package io.casehub.neocortex.cognition.prompt;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface CognitionPromptRenderer {
    @Nullable String render(CognitionRenderContext context);
}
