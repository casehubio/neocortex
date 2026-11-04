package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.entity.token.EntityIdentity;
import io.casehub.neocortex.entity.token.EntityRenderer;
import io.casehub.neocortex.entity.token.RenderMode;
import io.casehub.neocortex.entity.token.TokenFormat;
import org.jspecify.annotations.Nullable;

import java.util.function.BiFunction;

public class EntityRenderingSection implements CognitionPromptRenderer {

    private final CognitionPromptRenderer delegate;
    private final @Nullable BiFunction<String, String, EntityIdentity> identityLookup;

    public EntityRenderingSection(CognitionPromptRenderer delegate,
                                  @Nullable BiFunction<String, String, EntityIdentity> identityLookup) {
        this.delegate       = delegate;
        this.identityLookup = identityLookup;
    }

    @Override
    public @Nullable String render(CognitionRenderContext context) {
        String rendered = delegate.render(context);
        if (rendered == null || !rendered.contains("{{")) {
            return rendered;
        }
        if (identityLookup != null) {
            return EntityRenderer.render(rendered,
                    entityId -> identityLookup.apply(entityId, context.tenantId()),
                    RenderMode.PROSE);
        }
        return TokenFormat.stripTokens(rendered);
    }

    @Override
    public BlockTag blockTag() {
        return delegate.blockTag();
    }

    CognitionPromptRenderer delegate() {
        return delegate;
    }
}
