package io.casehub.neocortex.knowledge;

@FunctionalInterface
public interface TermNormalizer {
    ExpandedTerm normalize(String term, String domain);
}
