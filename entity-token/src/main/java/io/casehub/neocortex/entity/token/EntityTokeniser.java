package io.casehub.neocortex.entity.token;

import java.util.List;

public final class EntityTokeniser {

    private EntityTokeniser() {}

    public static String tokenise(String text, SurfaceFormTrie trie) {
        if (text == null || text.isEmpty()) return text;

        List<TrieMatch> matches = trie.scan(text);
        if (matches.isEmpty()) return text;

        StringBuilder sb = new StringBuilder(text.length() + matches.size() * 20);
        int lastEnd = 0;
        for (TrieMatch match : matches) {
            sb.append(text, lastEnd, match.startOffset());
            String originalCasing = text.substring(match.startOffset(), match.endOffset());
            sb.append(TokenFormat.emit(match.form().entityId(), match.form().variant(), originalCasing));
            lastEnd = match.endOffset();
        }
        sb.append(text, lastEnd, text.length());
        return sb.toString();
    }
}
