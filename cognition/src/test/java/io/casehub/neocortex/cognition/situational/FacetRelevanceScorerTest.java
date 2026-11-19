package io.casehub.neocortex.cognition.situational;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FacetRelevanceScorerTest {

    private static final ShadowFrameConfig CONFIG = ShadowFrameConfig.defaults();

    @Test
    void modeCharacteristicFacetsRankHigher() {
        var displacements = Map.of(
            "anxiousness", 0.08,
            "grandiosity", 0.10
        );
        Set<String> modeProfileFacets = Set.of("anxiousness", "emotional-lability");
        var scored = FacetRelevanceScorer.score(displacements, "STRESS", modeProfileFacets, null, CONFIG);

        assertThat(scored).hasSize(2);
        assertThat(scored.getFirst().facet()).isEqualTo("anxiousness");
        assertThat(scored.getFirst().modeCharacteristic()).isTrue();
        assertThat(scored.get(1).facet()).isEqualTo("grandiosity");
        assertThat(scored.get(1).modeCharacteristic()).isFalse();
    }

    @Test
    void attractorCorrelatedFacetsGetBoosted() {
        var displacements = Map.of(
            "anxiousness", 0.08,
            "hostility", 0.09
        );
        var config = new ShadowFrameConfig(5, 0.05, 1.5, 1.3,
            Map.of("withdraw", List.of("anxiousness", "withdrawal", "intimacy-avoidance")));
        var scored = FacetRelevanceScorer.score(displacements, null, Set.of(), "withdraw", config);

        assertThat(scored.getFirst().facet()).isEqualTo("anxiousness");
        assertThat(scored.getFirst().attractorCorrelated()).isTrue();
    }

    @Test
    void belowMinDisplacementFiltered() {
        var displacements = Map.of(
            "anxiousness", 0.08,
            "grandiosity", 0.02
        );
        var scored = FacetRelevanceScorer.score(displacements, null, Set.of(), null, CONFIG);
        assertThat(scored).hasSize(1);
        assertThat(scored.getFirst().facet()).isEqualTo("anxiousness");
    }

    @Test
    void cappedAtMaxFacets() {
        var displacements = Map.of(
            "anxiousness", 0.5, "grandiosity", 0.4, "hostility", 0.3,
            "impulsivity", 0.2, "withdrawal", 0.15, "suspiciousness", 0.1,
            "risk-taking", 0.08
        );
        var config = new ShadowFrameConfig(3, 0.05, 1.5, 1.3, Map.of());
        var scored = FacetRelevanceScorer.score(displacements, null, Set.of(), null, config);
        assertThat(scored).hasSize(3);
    }

    @Test
    void combinedBoostsStack() {
        var displacements = Map.of(
            "anxiousness", 0.06,
            "hostility", 0.10
        );
        Set<String> modeProfileFacets = Set.of("anxiousness");
        var config = new ShadowFrameConfig(5, 0.05, 1.5, 1.3,
            Map.of("withdraw", List.of("anxiousness")));
        var scored = FacetRelevanceScorer.score(displacements, "STRESS", modeProfileFacets, "withdraw", config);

        // anxiousness: 0.06 * 1.5 * 1.3 = 0.117
        // hostility: 0.10 * 1.0 * 1.0 = 0.100
        assertThat(scored.getFirst().facet()).isEqualTo("anxiousness");
    }
}
