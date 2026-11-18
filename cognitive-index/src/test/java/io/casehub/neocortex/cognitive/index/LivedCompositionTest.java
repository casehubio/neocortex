package io.casehub.neocortex.cognitive.index;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LivedCompositionTest {

    @Test
    void fingerprintIsDeterministic() {
        var profile = List.of(new WeightedTerm("ti", 0.35), new WeightedTerm("ne", 0.20));
        var fp1 = LivedComposition.fingerprint(profile);
        var fp2 = LivedComposition.fingerprint(profile);
        assertThat(fp1).isEqualTo(fp2);
        assertThat(fp1).isNotEmpty();
    }

    @Test
    void fingerprintIsOrderIndependent() {
        var p1 = List.of(new WeightedTerm("ti", 0.35), new WeightedTerm("ne", 0.20));
        var p2 = List.of(new WeightedTerm("ne", 0.20), new WeightedTerm("ti", 0.35));
        assertThat(LivedComposition.fingerprint(p1)).isEqualTo(LivedComposition.fingerprint(p2));
    }

    @Test
    void profileReturnsLivedWhenFingerprintMatches() {
        var authored = List.of(new WeightedTerm("ti", 0.35), new WeightedTerm("ne", 0.20));
        var livedTerms = List.of(new WeightedTerm("ne", 0.35), new WeightedTerm("ti", 0.20));
        var fp = LivedComposition.fingerprint(authored);
        var lived = new LivedProfile(livedTerms, fp);

        var result = LivedComposition.profile(authored, lived);
        assertThat(result).isEqualTo(livedTerms);
    }

    @Test
    void profileReturnsAuthoredWhenFingerprintMismatches() {
        var authored = List.of(new WeightedTerm("ti", 0.35), new WeightedTerm("ne", 0.20));
        var livedTerms = List.of(new WeightedTerm("ne", 0.35), new WeightedTerm("ti", 0.20));
        var lived = new LivedProfile(livedTerms, "wrong-fingerprint");

        var result = LivedComposition.profile(authored, lived);
        assertThat(result).isEqualTo(authored);
    }

    @Test
    void profileReturnsAuthoredWhenLivedIsNull() {
        var authored = List.of(new WeightedTerm("ti", 0.35), new WeightedTerm("ne", 0.20));
        assertThat(LivedComposition.profile(authored, null)).isEqualTo(authored);
    }

    @Test
    void composeRestingFacetsCorrectly() {
        var authored = new DescriptorView("agent1", null, List.of(), List.of(), null,
                List.of(new PersonalityFacet("Anxiousness", 0.50, "authored")));
        var facets = Map.of("Anxiousness",
                new PersonalityStateSnapshot.FacetDisplacement(0.05, 0.02, 0.10));
        var snapshot = new PersonalityStateSnapshot(facets, 1, 1, null);

        var resting = LivedComposition.compose(authored, snapshot, false);
        assertThat(resting.personalityFacets().getFirst().score())
                .isCloseTo(0.65, within(1e-9));
    }

    @Test
    void composeEffectiveFacetsIncludesDisplacement() {
        var authored = new DescriptorView("agent1", null, List.of(), List.of(), null,
                List.of(new PersonalityFacet("Anxiousness", 0.50, "authored")));
        var facets = Map.of("Anxiousness",
                new PersonalityStateSnapshot.FacetDisplacement(0.05, 0.02, 0.10));
        var snapshot = new PersonalityStateSnapshot(facets, 1, 1, null);

        var effective = LivedComposition.compose(authored, snapshot, true);
        assertThat(effective.personalityFacets().getFirst().score())
                .isCloseTo(0.67, within(1e-9));
    }

    @Test
    void composeClampsFacetScoreTo01() {
        var authored = new DescriptorView("agent1", null, List.of(), List.of(), null,
                List.of(new PersonalityFacet("Anxiousness", 0.85, "authored")));
        var facets = Map.of("Anxiousness",
                new PersonalityStateSnapshot.FacetDisplacement(0.09, 0.0, 0.10));
        var snapshot = new PersonalityStateSnapshot(facets, 1, 1, null);

        var result = LivedComposition.compose(authored, snapshot, false);
        assertThat(result.personalityFacets().getFirst().score()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void authoredEditResetsMakesLivedProfileDormant() {
        var originalAuthored = List.of(new WeightedTerm("ti", 0.35), new WeightedTerm("ne", 0.20));
        var fp = LivedComposition.fingerprint(originalAuthored);
        var livedTerms = List.of(new WeightedTerm("ne", 0.35), new WeightedTerm("ti", 0.20));
        var lived = new LivedProfile(livedTerms, fp);

        var newAuthored = List.of(new WeightedTerm("si", 0.35), new WeightedTerm("ne", 0.20));
        var result = LivedComposition.profile(newAuthored, lived);
        assertThat(result).isEqualTo(newAuthored);
    }

    @Test
    void authoredRevertReactivatesLivedProfile() {
        var originalAuthored = List.of(new WeightedTerm("ti", 0.35), new WeightedTerm("ne", 0.20));
        var fp = LivedComposition.fingerprint(originalAuthored);
        var livedTerms = List.of(new WeightedTerm("ne", 0.35), new WeightedTerm("ti", 0.20));
        var lived = new LivedProfile(livedTerms, fp);

        var result = LivedComposition.profile(originalAuthored, lived);
        assertThat(result).isEqualTo(livedTerms);
    }

    @Test
    void facetReAuthoringKeepsPromotedComposedOverNewBaseline() {
        var authored = new DescriptorView("agent1", null, List.of(), List.of(), null,
                List.of(new PersonalityFacet("Anxiousness", 0.60, "authored")));
        var facets = Map.of("Anxiousness",
                new PersonalityStateSnapshot.FacetDisplacement(0.0, 0.0, 0.10));
        var snapshot = new PersonalityStateSnapshot(facets, 1, 1, null);

        var result = LivedComposition.compose(authored, snapshot, false);
        assertThat(result.personalityFacets().getFirst().score())
                .isCloseTo(0.70, within(1e-9));
    }
}
