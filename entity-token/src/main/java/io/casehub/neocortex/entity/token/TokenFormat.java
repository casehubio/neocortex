package io.casehub.neocortex.entity.token;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TokenFormat {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{([^:}]+):([^:}]+):(.+?)}}");

    private TokenFormat() {}

    public static List<ParsedToken> parse(String text) {
        if (text == null || text.isEmpty()) return List.of();

        List<ParsedToken> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text);

        while (matcher.find()) {
            String entityId = matcher.group(1);
            String variantOrPronoun = matcher.group(2);
            String rest = matcher.group(3);

            String variant;
            String surfaceForm;

            if ("pronoun".equals(variantOrPronoun)) {
                int colonIdx = rest.indexOf(':');
                if (colonIdx > 0 && colonIdx < rest.length() - 1) {
                    variant = "pronoun:" + rest.substring(0, colonIdx);
                    surfaceForm = rest.substring(colonIdx + 1);
                } else {
                    variant = variantOrPronoun;
                    surfaceForm = rest;
                }
            } else {
                variant = variantOrPronoun;
                surfaceForm = rest;
            }

            tokens.add(new ParsedToken(entityId, variant, surfaceForm,
                matcher.start(), matcher.end()));
        }

        return List.copyOf(tokens);
    }

    public static String emit(String entityId, String variant, String surfaceForm) {
        return "{{" + entityId + ":" + variant + ":" + surfaceForm + "}}";
    }

    public static Set<String> extractEntityIds(String text) {
        Set<String> ids = new LinkedHashSet<>();
        for (ParsedToken token : parse(text)) {
            ids.add(token.entityId());
        }
        return Set.copyOf(ids);
    }

    public static Set<String> extractEntityIds(String text, SurfaceFormTrie trie) {
        Set<String> fromTokens = extractEntityIds(text);
        if (!fromTokens.isEmpty()) {
            return fromTokens;
        }
        if (trie == null) {
            return Set.of();
        }
        List<TrieMatch> matches = trie.scan(text);
        Set<String>     ids     = new LinkedHashSet<>();
        for (TrieMatch match : matches) {
            ids.add(match.form().entityId());
        }
        return Set.copyOf(ids);
    }


    public static String stripTokens(String text) {
        if (text == null || !text.contains("{{")) return text;

        List<ParsedToken> tokens = parse(text);
        if (tokens.isEmpty()) return text;

        StringBuilder sb = new StringBuilder(text.length());
        int lastEnd = 0;
        for (ParsedToken token : tokens) {
            sb.append(text, lastEnd, token.startOffset());
            sb.append(token.surfaceForm());
            lastEnd = token.endOffset();
        }
        sb.append(text, lastEnd, text.length());
        return sb.toString();
    }
}
