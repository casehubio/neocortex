package io.casehub.neocortex.knowledge.normalization;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExpansionStrategyTest {

    @Test
    void knownProviderReturnsCanonicalOnly() {
        var strategy = new ExpansionStrategy(Set.of("google"));
        assertThat(strategy.strategyFor("google"))
            .isEqualTo(ExpansionStrategy.Mode.CANONICAL_ONLY);
    }

    @Test
    void unknownProviderReturnsCanonicalPlusVariants() {
        var strategy = new ExpansionStrategy(Set.of("google"));
        assertThat(strategy.strategyFor("other"))
            .isEqualTo(ExpansionStrategy.Mode.CANONICAL_PLUS_VARIANTS);
    }

    @Test
    void emptyKnownSetExpandsAll() {
        var strategy = new ExpansionStrategy(Set.of());
        assertThat(strategy.strategyFor("google"))
            .isEqualTo(ExpansionStrategy.Mode.CANONICAL_PLUS_VARIANTS);
    }
}
