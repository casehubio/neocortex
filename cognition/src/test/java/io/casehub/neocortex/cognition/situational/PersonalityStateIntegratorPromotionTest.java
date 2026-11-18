package io.casehub.neocortex.cognition.situational;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PersonalityStateIntegratorPromotionTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private PersonalityStateIntegrator integrator;

    @BeforeEach
    void setUp() {
        integrator = new PersonalityStateIntegrator(DynamicsConfig.defaults());
    }

    @Test
    void dwellClockMet_candidateReturned() {
        var facets = new LinkedHashMap<String, FacetState>();
        var saturatedSince = NOW.minus(Duration.ofDays(31));
        facets.put("Anxiousness", new FacetState(0.0, 0.09, 1.0, 0, 0, 0.0, saturatedSince, null));
        var state = new PersonalityState(facets, Map.of(), Map.of(), Map.of(), NOW, NOW, Instant.EPOCH);
        var baselines = Map.of("Anxiousness", 0.5);

        var candidates = integrator.promotionCandidates(state, baselines, NOW);
        assertThat(candidates).containsKey("Anxiousness");
        assertThat(candidates.get("Anxiousness").delta()).isGreaterThan(0);
    }

    @Test
    void dwellClockNotMet_noCandidate() {
        var facets = new LinkedHashMap<String, FacetState>();
        var saturatedSince = NOW.minus(Duration.ofDays(29));
        facets.put("Anxiousness", new FacetState(0.0, 0.09, 1.0, 0, 0, 0.0, saturatedSince, null));
        var state = new PersonalityState(facets, Map.of(), Map.of(), Map.of(), NOW, NOW, Instant.EPOCH);
        var baselines = Map.of("Anxiousness", 0.5);

        var candidates = integrator.promotionCandidates(state, baselines, NOW);
        assertThat(candidates).isEmpty();
    }

    @Test
    void budgetExceeded_noCandidate() {
        var facets = new LinkedHashMap<String, FacetState>();
        var saturatedSince = NOW.minus(Duration.ofDays(31));
        facets.put("Anxiousness", new FacetState(0.0, 0.09, 1.0, 0, 0, 0.15, saturatedSince, null));
        var state = new PersonalityState(facets, Map.of(), Map.of(), Map.of(), NOW, NOW, Instant.EPOCH);
        var baselines = Map.of("Anxiousness", 0.5);

        var candidates = integrator.promotionCandidates(state, baselines, NOW);
        assertThat(candidates).isEmpty();
    }

    @Test
    void deltaComputation_optionA() {
        var facets = new LinkedHashMap<String, FacetState>();
        var saturatedSince = NOW.minus(Duration.ofDays(31));
        facets.put("Anxiousness", new FacetState(0.0, 0.09, 1.0, 0, 0, 0.10, saturatedSince, null));
        var state = new PersonalityState(facets, Map.of(), Map.of(), Map.of(), NOW, NOW, Instant.EPOCH);
        var baselines = Map.of("Anxiousness", 0.85);

        var candidates = integrator.promotionCandidates(state, baselines, NOW);
        assertThat(candidates).containsKey("Anxiousness");
        double expected = Math.min(1.0, 0.85 + 0.10 + 0.09) - (0.85 + 0.10);
        assertThat(candidates.get("Anxiousness").delta()).isCloseTo(expected, within(1e-9));
    }

    @Test
    void restingScoreContinuity() {
        double baseline = 0.50;
        double promoted = 0.0;
        double homeOffset = 0.09;

        double restingBefore = Math.min(1.0, Math.max(0.0, baseline + promoted + homeOffset));

        var facets = new LinkedHashMap<String, FacetState>();
        var saturatedSince = NOW.minus(Duration.ofDays(31));
        facets.put("Anxiousness", new FacetState(0.0, homeOffset, 1.0, 0, 0, promoted, saturatedSince, null));
        var state = new PersonalityState(facets, Map.of(), Map.of(), Map.of(), NOW, NOW, Instant.EPOCH);
        var baselines = Map.of("Anxiousness", baseline);

        var candidates = integrator.promotionCandidates(state, baselines, NOW);
        var candidate = candidates.get("Anxiousness");
        assertThat(candidate).isNotNull();

        double newPromoted = promoted + candidate.delta();
        double newHomeOffset = homeOffset - candidate.delta();
        double restingAfter = Math.min(1.0, Math.max(0.0, baseline + newPromoted + newHomeOffset));
        assertThat(restingAfter).isCloseTo(restingBefore, within(1e-9));
    }

    @Test
    void noSaturatedSince_noCandidate() {
        var facets = new LinkedHashMap<String, FacetState>();
        facets.put("Anxiousness", new FacetState(0.0, 0.09, 1.0, 0, 0, 0.0, null, null));
        var state = new PersonalityState(facets, Map.of(), Map.of(), Map.of(), NOW, NOW, Instant.EPOCH);
        var baselines = Map.of("Anxiousness", 0.5);

        var candidates = integrator.promotionCandidates(state, baselines, NOW);
        assertThat(candidates).isEmpty();
    }
}
