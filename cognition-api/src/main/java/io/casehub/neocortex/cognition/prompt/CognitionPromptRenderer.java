package io.casehub.neocortex.cognition.prompt;

import org.jspecify.annotations.Nullable;

public interface CognitionPromptRenderer {
    @Nullable String render(CognitionRenderContext context);

    default BlockTag blockTag() {
        return null;
    }
}
