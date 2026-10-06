package io.casehub.neocortex.knowledge.normalization;

import java.util.Set;

public class ExpansionStrategy {

    public enum Mode { CANONICAL_ONLY, CANONICAL_PLUS_VARIANTS }

    private final Set<String> knownProviders;

    public ExpansionStrategy(Set<String> knownProviders) {
        this.knownProviders = Set.copyOf(knownProviders);
    }

    public Mode strategyFor(String providerId) {
        return knownProviders.contains(providerId)
            ? Mode.CANONICAL_ONLY
            : Mode.CANONICAL_PLUS_VARIANTS;
    }
}
