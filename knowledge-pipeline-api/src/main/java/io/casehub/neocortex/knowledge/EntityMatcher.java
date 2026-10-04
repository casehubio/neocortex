package io.casehub.neocortex.knowledge;

@FunctionalInterface
public interface EntityMatcher<T> {

    MatchResult match(T candidate, T existing);
}
