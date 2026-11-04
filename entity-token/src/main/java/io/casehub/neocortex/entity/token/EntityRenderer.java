package io.casehub.neocortex.entity.token;

import java.util.List;
import java.util.function.Function;

public final class EntityRenderer {

    private EntityRenderer() {}

    public static String render(String text,
                                Function<String, EntityIdentity> identityLookup,
                                RenderMode mode) {
        if (text == null || !text.contains("{{")) return text;

        List<ParsedToken> tokens = TokenFormat.parse(text);
        if (tokens.isEmpty()) return text;

        StringBuilder sb = new StringBuilder(text.length());
        int lastEnd = 0;
        for (ParsedToken token : tokens) {
            sb.append(text, lastEnd, token.startOffset());
            sb.append(resolveToken(token, identityLookup, mode));
            lastEnd = token.endOffset();
        }
        sb.append(text, lastEnd, text.length());
        return sb.toString();
    }

    private static String resolveToken(ParsedToken token,
                                       Function<String, EntityIdentity> identityLookup,
                                       RenderMode mode) {
        EntityIdentity identity = identityLookup.apply(token.entityId());
        if (identity == null) return token.surfaceForm();

        String resolved = identity.resolveVariant(token.variant());
        if (resolved == null) return token.surfaceForm();

        if (mode == RenderMode.ANNOTATED && token.variant().startsWith("pronoun:")) {
            String label = identity.resolveVariant("canonicalFirst");
            if (label == null) label = identity.canonicalFull();
            return resolved + " [" + label + "]";
        }

        return resolved;
    }
}
