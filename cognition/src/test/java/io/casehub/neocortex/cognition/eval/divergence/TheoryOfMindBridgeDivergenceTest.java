package io.casehub.neocortex.cognition.eval.divergence;

import io.casehub.neocortex.cognition.appraisal.ActionReadiness;
import io.casehub.neocortex.cognition.appraisal.ActionTendency;
import io.casehub.neocortex.cognition.appraisal.AppraisalResult;
import io.casehub.neocortex.cognition.appraisal.HabituationState;
import io.casehub.neocortex.cognition.mentalmodel.TheoryOfMindPosterior;
import io.casehub.neocortex.cognition.tom.TheoryOfMindBridge;
import io.casehub.neocortex.cognitive.index.PersonalityFacet;
import io.casehub.neocortex.mindmap.intelligence.tom.TomPersonalityModulation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TheoryOfMindBridgeDivergenceTest {

    private final TheoryOfMindBridge bridge = new TheoryOfMindBridge();

    private static final TomPersonalityModulation OPEN_MODULATION = modulationFromFacets(DivergenceFixtures.OPEN_FACETS);
    private static final TomPersonalityModulation CAUTIOUS_MODULATION = modulationFromFacets(DivergenceFixtures.CAUTIOUS_FACETS);

    @Test
    void posteriorsDivergeOnAmbiguousAppraisal() {
        var appraisal = appraisalWith(
                Map.of("conduciveness", -0.3, "controllability", 0.5),
                List.of(new ActionTendency(ActionReadiness.ATTENDING, 0.5, "")));

        var openPosterior = bridge.infer(appraisal, OPEN_MODULATION);
        var cautiousPosterior = bridge.infer(appraisal, CAUTIOUS_MODULATION);

        assertThat(posteriorsDiffer(openPosterior, cautiousPosterior))
                .as("posteriors should diverge on same ambiguous appraisal — open=%s vs cautious=%s",
                        format(openPosterior), format(cautiousPosterior))
                .isTrue();
    }

    @Test
    void cautiousInfersMoreDistressFromMildThreat() {
        // Neutral behavior isolates the anxiousness-driven threat prior shift;
        // avoidant behavior would let callousness (Blair 2005) weaken distress coupling
        var appraisal = appraisalWith(
                Map.of("conduciveness", -0.4, "controllability", 0.4),
                List.of(new ActionTendency(ActionReadiness.ATTENDING, 0.6, "")));

        var openPosterior = bridge.infer(appraisal, OPEN_MODULATION);
        var cautiousPosterior = bridge.infer(appraisal, CAUTIOUS_MODULATION);

        assertThat(cautiousPosterior.distressed())
                .as("cautious agent (anxiousness=%.2f) should infer more distress from mild threat — "
                    + "open=%.3f vs cautious=%.3f",
                        CAUTIOUS_MODULATION.anxiousness(),
                        openPosterior.distressed(), cautiousPosterior.distressed())
                .isGreaterThan(openPosterior.distressed());
    }

    @Test
    void openInfersMorePositiveFromCooperativeApproach() {
        var appraisal = appraisalWith(
                Map.of("conduciveness", 0.5, "controllability", 0.7),
                List.of(new ActionTendency(ActionReadiness.APPROACH, 0.8, "")));

        var openPosterior = bridge.infer(appraisal, OPEN_MODULATION);
        var cautiousPosterior = bridge.infer(appraisal, CAUTIOUS_MODULATION);

        assertThat(openPosterior.positive())
                .as("open agent (anxiousness=%.2f, suspiciousness=%.2f) should infer more positive — "
                    + "open=%.3f vs cautious=%.3f",
                        OPEN_MODULATION.anxiousness(), OPEN_MODULATION.suspiciousness(),
                        openPosterior.positive(), cautiousPosterior.positive())
                .isGreaterThan(cautiousPosterior.positive());
    }

    @Test
    void suspiciousnessReinterpretsApproachBehavior() {
        var appraisal = appraisalWith(
                Map.of("conduciveness", -0.2, "controllability", 0.5),
                List.of(new ActionTendency(ActionReadiness.APPROACH, 0.7, ""),
                        new ActionTendency(ActionReadiness.DOMINANCE, 0.3, "")));

        var openPosterior = bridge.infer(appraisal, OPEN_MODULATION);
        var cautiousPosterior = bridge.infer(appraisal, CAUTIOUS_MODULATION);

        assertThat(cautiousPosterior.distressed())
                .as("suspicious agent should read approach+dominance as more threatening — "
                    + "open distressed=%.3f vs cautious distressed=%.3f",
                        openPosterior.distressed(), cautiousPosterior.distressed())
                .isGreaterThan(openPosterior.distressed());
    }

    private static TomPersonalityModulation modulationFromFacets(List<PersonalityFacet> facets) {
        double anxiousness = facetScore(facets, PersonalityFacet.ANXIOUSNESS, 0.5);
        double callousness = facetScore(facets, PersonalityFacet.CALLOUSNESS, 0.0);
        double suspiciousness = facetScore(facets, PersonalityFacet.SUSPICIOUSNESS, 0.0);
        return new TomPersonalityModulation(anxiousness, callousness, suspiciousness);
    }

    private static double facetScore(List<PersonalityFacet> facets, String name, double defaultValue) {
        return facets.stream()
                .filter(f -> name.equals(f.facet()))
                .findFirst()
                .map(PersonalityFacet::score)
                .orElse(defaultValue);
    }

    private static boolean posteriorsDiffer(TheoryOfMindPosterior a, TheoryOfMindPosterior b) {
        return Math.abs(a.positive() - b.positive()) > 0.001
                || Math.abs(a.neutral() - b.neutral()) > 0.001
                || Math.abs(a.distressed() - b.distressed()) > 0.001;
    }

    private static String format(TheoryOfMindPosterior p) {
        return String.format("[pos=%.3f, neu=%.3f, dis=%.3f]", p.positive(), p.neutral(), p.distressed());
    }

    private static AppraisalResult appraisalWith(Map<String, Double> secDimensions,
                                                  List<ActionTendency> tendencies) {
        return new AppraisalResult(List.of(), tendencies, HabituationState.empty(), null, secDimensions);
    }
}
