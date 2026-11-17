package io.casehub.neocortex.cognition;

import io.casehub.neocortex.cognition.eval.CognitionTestHarness;
import io.casehub.neocortex.cognitive.index.CognitiveDerivationEngine;
import io.casehub.neocortex.cognitive.index.CopingDefaults;
import io.casehub.neocortex.cognitive.index.DescriptorView;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.cognitive.index.PersonalityFacet;
import io.casehub.neocortex.cognitive.index.WeightedTerm;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalityDifferentiationTest {

    private static final String TENANT = "test";

    private static final DispositionAxes OPEN_AXES =
            new DispositionAxes("cooperative", "moderate", "bold", "high", "cooperative");

    private static final List<PersonalityFacet> OPEN_FACETS = List.of(
            new PersonalityFacet(PersonalityFacet.ANXIOUSNESS, 0.2, "test"),
            new PersonalityFacet(PersonalityFacet.EMOTIONAL_LABILITY, 0.2, "test"),
            new PersonalityFacet(PersonalityFacet.WITHDRAWAL, 0.1, "test"),
            new PersonalityFacet(PersonalityFacet.INTIMACY_AVOIDANCE, 0.1, "test"),
            new PersonalityFacet(PersonalityFacet.RISK_TAKING, 0.8, "test"),
            new PersonalityFacet(PersonalityFacet.SUSPICIOUSNESS, 0.2, "test"),
            new PersonalityFacet(PersonalityFacet.CALLOUSNESS, 0.1, "test"),
            new PersonalityFacet(PersonalityFacet.HOSTILITY, 0.1, "test"),
            new PersonalityFacet(PersonalityFacet.MANIPULATIVENESS, 0.1, "test"),
            new PersonalityFacet(PersonalityFacet.IMPULSIVITY, 0.3, "test"));

    private static final DispositionAxes CAUTIOUS_AXES =
            new DispositionAxes("independent", "strict", "conservative", "low", "avoidant");

    private static final List<PersonalityFacet> CAUTIOUS_FACETS = List.of(
            new PersonalityFacet(PersonalityFacet.ANXIOUSNESS, 0.85, "test"),
            new PersonalityFacet(PersonalityFacet.EMOTIONAL_LABILITY, 0.8, "test"),
            new PersonalityFacet(PersonalityFacet.WITHDRAWAL, 0.8, "test"),
            new PersonalityFacet(PersonalityFacet.INTIMACY_AVOIDANCE, 0.7, "test"),
            new PersonalityFacet(PersonalityFacet.RISK_TAKING, 0.2, "test"),
            new PersonalityFacet(PersonalityFacet.SUSPICIOUSNESS, 0.7, "test"),
            new PersonalityFacet(PersonalityFacet.CALLOUSNESS, 0.4, "test"),
            new PersonalityFacet(PersonalityFacet.HOSTILITY, 0.5, "test"),
            new PersonalityFacet(PersonalityFacet.MANIPULATIVENESS, 0.3, "test"),
            new PersonalityFacet(PersonalityFacet.IMPULSIVITY, 0.5, "test"));

    @Test
    void derivedCopingDefaultsDiverge() {
        var openDefaults = deriveCopingDefaults("open", OPEN_AXES, OPEN_FACETS);
        var cautiousDefaults = deriveCopingDefaults("cautious", CAUTIOUS_AXES, CAUTIOUS_FACETS);

        assertThat(openDefaults).isNotNull();
        assertThat(cautiousDefaults).isNotNull();

        assertThat(cautiousDefaults.avoidanceTendency())
                .as("cautious agent should be more avoidant (high neuroticism)")
                .isGreaterThan(openDefaults.avoidanceTendency());
        assertThat(openDefaults.confrontationTendency())
                .as("open agent should be more confrontational (high extraversion)")
                .isGreaterThan(cautiousDefaults.confrontationTendency());
        assertThat(cautiousDefaults.disengagementTendency())
                .as("cautious agent should disengage more (high N, low C)")
                .isGreaterThan(openDefaults.disengagementTendency());
        assertThat(cautiousDefaults.suppressionTendency())
                .as("cautious agent should suppress more (high N + strict rules)")
                .isGreaterThan(openDefaults.suppressionTendency());
}

    @Test
    void searchBudgetsDivergeAfterTick() {
        var openView = new DescriptorView("open", OPEN_AXES,
                List.of(new WeightedTerm("ne", 0.5), new WeightedTerm("se", 0.5)),
                List.of(), null, OPEN_FACETS);
        var cautiousView = new DescriptorView("cautious", CAUTIOUS_AXES,
                List.of(new WeightedTerm("si", 0.5), new WeightedTerm("te", 0.5)),
                List.of(), null, CAUTIOUS_FACETS);

        var openDefaults = CognitiveDerivationEngine.derive(openView);
        var cautiousDefaults = CognitiveDerivationEngine.derive(cautiousView);

        var harness = CognitionTestHarness.builder()
                .agentFromDefaults("open", openDefaults)
                .agentFromDefaults("cautious", cautiousDefaults)
                .build();

        harness.observe("open", TENANT, "someone shared an unexpected idea");
        harness.tick("open", TENANT);
        harness.observe("cautious", TENANT, "someone shared an unexpected idea");
        harness.tick("cautious", TENANT);

        var openBudget = harness.searchBudget("open", TENANT);
        var cautiousBudget = harness.searchBudget("cautious", TENANT);

        assertThat(openBudget).isPresent();
        assertThat(cautiousBudget).isPresent();

        var ob = openBudget.get();
        var cb = cautiousBudget.get();

        boolean anyDimDiffers = ob.breadth() != cb.breadth()
                || ob.depth() != cb.depth()
                || ob.laterality() != cb.laterality()
                || ob.convergence() != cb.convergence();

        assertThat(anyDimDiffers)
                .as("search budgets should differ on at least one dimension — " +
                    "open[b=%.3f d=%.3f l=%.3f c=%.3f] vs cautious[b=%.3f d=%.3f l=%.3f c=%.3f]",
                    ob.breadth(), ob.depth(), ob.laterality(), ob.convergence(),
                    cb.breadth(), cb.depth(), cb.laterality(), cb.convergence())
                .isTrue();
}

    @Test
    void promptRenderingDiverges() {
        var openView = new DescriptorView("open", OPEN_AXES,
                List.of(new WeightedTerm("ne", 0.5), new WeightedTerm("se", 0.5)),
                List.of(), null, OPEN_FACETS);
        var cautiousView = new DescriptorView("cautious", CAUTIOUS_AXES,
                List.of(new WeightedTerm("si", 0.5), new WeightedTerm("te", 0.5)),
                List.of(), null, CAUTIOUS_FACETS);

        var openDefaults = CognitiveDerivationEngine.derive(openView);
        var cautiousDefaults = CognitiveDerivationEngine.derive(cautiousView);

        var harness = CognitionTestHarness.builder()
                .agentFromDefaults("open", openDefaults)
                .agentFromDefaults("cautious", cautiousDefaults)
                .build();

        harness.observe("open", TENANT, "someone shared an unexpected idea");
        harness.tick("open", TENANT);
        var openPrompt = harness.renderPrompt("open", TENANT);

        harness.observe("cautious", TENANT, "someone shared an unexpected idea");
        harness.tick("cautious", TENANT);
        var cautiousPrompt = harness.renderPrompt("cautious", TENANT);

        assertThat(openPrompt).isNotEqualTo(cautiousPrompt);
    }

    private static CopingDefaults deriveCopingDefaults(String agentId,
                                                        DispositionAxes axes,
                                                        List<PersonalityFacet> facets) {
        var view = new DescriptorView(agentId, axes,
                List.of(new WeightedTerm("ne", 0.5)), List.of(), null, facets);
        return CognitiveDerivationEngine.derive(view).copingDefaults();
    }
}
