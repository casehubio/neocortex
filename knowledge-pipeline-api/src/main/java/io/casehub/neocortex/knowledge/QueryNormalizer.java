package io.casehub.neocortex.knowledge;

@FunctionalInterface
public interface QueryNormalizer {

    NormalizedQuery normalize(String naturalLanguage);
}
