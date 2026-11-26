package io.casehub.neocortex.cognitive.index;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Pid5FacetPipelineIntegrationTest {

    private static final DispositionAxes AXES =
            new DispositionAxes("collaborative", "principled", "measured", "semi-autonomous", "compromising");

    private static final List<WeightedTerm> PROFILE = List.of(
            new WeightedTerm("ni", 0.3), new WeightedTerm("te", 0.25),
            new WeightedTerm("fi", 0.25), new WeightedTerm("se", 0.2));

    @Test
    void endToEnd_25FacetsFlowThroughDerivation() {
        var facets = List.of(
                new PersonalityFacet(PersonalityFacet.ANHEDONIA, 0.8, "test"),
                new PersonalityFacet(PersonalityFacet.DEPRESSIVITY, 0.75, "test"),
                new PersonalityFacet(PersonalityFacet.RIGID_PERFECTIONISM, 0.85, "test"),
                new PersonalityFacet(PersonalityFacet.DISTRACTIBILITY, 0.7, "test"),
                new PersonalityFacet(PersonalityFacet.ECCENTRICITY, 0.6, "test"),
                new PersonalityFacet(PersonalityFacet.PERSEVERATION, 0.65, "test"),
                new PersonalityFacet(PersonalityFacet.SEPARATION_INSECURITY, 0.7, "test"),
                new PersonalityFacet(PersonalityFacet.RESTRICTED_AFFECTIVITY, 0.6, "test"),
                new PersonalityFacet(PersonalityFacet.IRRESPONSIBILITY, 0.55, "test"),
                new PersonalityFacet(PersonalityFacet.COGNITIVE_PERCEPTUAL_DYSREGULATION, 0.5, "test"),
                new PersonalityFacet(PersonalityFacet.SUBMISSIVENESS, 0.6, "test"),
                new PersonalityFacet(PersonalityFacet.UNUSUAL_BELIEFS, 0.5, "test"),
                new PersonalityFacet(PersonalityFacet.ANXIOUSNESS, 0.5, "test"),
                new PersonalityFacet(PersonalityFacet.CALLOUSNESS, 0.5, "test"));
        var view = new DescriptorView("test-agent", AXES, PROFILE, List.of("investigate"), null, facets);

        var defaults = CognitiveDerivationEngine.derive(view);

        assertThat(defaults).isNotNull();
        assertThat(defaults.moodBaseline()).isNotNull();
        assertThat(defaults.capacityConfig()).isNotNull();
        assertThat(defaults.searchBudgetConfig()).isNotNull();
        assertThat(defaults.regulationDefaults()).isNotNull();
        assertThat(defaults.cognitiveResourcePoolConfig()).isNotNull();
        assertThat(defaults.recurringThoughtConfig()).isNotNull();
    }

    @Test
    void restingVsStressed_facetShiftsProduceDifferentDerivations() {
        var restingFacets = List.of(
                new PersonalityFacet(PersonalityFacet.ANHEDONIA, 0.4, "test"),
                new PersonalityFacet(PersonalityFacet.DEPRESSIVITY, 0.35, "test"),
                new PersonalityFacet(PersonalityFacet.DISTRACTIBILITY, 0.3, "test"),
                new PersonalityFacet(PersonalityFacet.SEPARATION_INSECURITY, 0.4, "test"));

        var stressedFacets = List.of(
                new PersonalityFacet(PersonalityFacet.ANHEDONIA, 0.52, "test"),
                new PersonalityFacet(PersonalityFacet.DEPRESSIVITY, 0.50, "test"),
                new PersonalityFacet(PersonalityFacet.DISTRACTIBILITY, 0.42, "test"),
                new PersonalityFacet(PersonalityFacet.SEPARATION_INSECURITY, 0.52, "test"));

        var restingView = new DescriptorView("test-agent", AXES, PROFILE, List.of(), null, restingFacets);
        var stressedView = new DescriptorView("test-agent", AXES, PROFILE, List.of(), null, stressedFacets);

        var resting = CognitiveDerivationEngine.derive(restingView);
        var stressed = CognitiveDerivationEngine.derive(stressedView);

        assertThat(stressed.moodBaseline().pleasure())
                .as("Stress should reduce pleasure via anhedonia+depressivity shift")
                .isLessThan(resting.moodBaseline().pleasure());
    }

    @Test
    void metadataEnrichment_includesNewFacets() {
        var facets = List.of(
                new PersonalityFacet(PersonalityFacet.ANHEDONIA, 0.65, "test"),
                new PersonalityFacet(PersonalityFacet.DEPRESSIVITY, 0.40, "test"),
                new PersonalityFacet(PersonalityFacet.ECCENTRICITY, 0.55, "test"),
                new PersonalityFacet(PersonalityFacet.SUBMISSIVENESS, 0.70, "test"));

        var view = new DescriptorView("test-agent", AXES, PROFILE, List.of(), null, facets);
        var derived = CognitiveDerivationEngine.derive(view);
        var profile = LearningProfile.from(derived);
        var metadata = PersonalityMetadataEnricher.enrich(new HashMap<>(), profile);

        assertThat(metadata).containsKey("pid5.Anhedonia");
        assertThat(metadata).containsKey("pid5.Depressivity");
        assertThat(metadata).containsKey("pid5.Eccentricity");
        assertThat(metadata).containsKey("pid5.Submissiveness");
        assertThat(metadata.get("pid5.Anhedonia")).isEqualTo("0.65");
    }

    @Test
    void multicollinearity_extremeNegativeAffectStaysBounded() {
        var facets = List.of(
                new PersonalityFacet(PersonalityFacet.ANXIOUSNESS, 0.95, "test"),
                new PersonalityFacet(PersonalityFacet.DEPRESSIVITY, 0.95, "test"),
                new PersonalityFacet(PersonalityFacet.PERSEVERATION, 0.95, "test"),
                new PersonalityFacet(PersonalityFacet.SEPARATION_INSECURITY, 0.95, "test"),
                new PersonalityFacet(PersonalityFacet.EMOTIONAL_LABILITY, 0.95, "test"));
        var view = new DescriptorView("test-agent", AXES, PROFILE, List.of(), null, facets);

        var defaults = CognitiveDerivationEngine.derive(view);

        assertThat(defaults.recurringThoughtConfig().fixationThreshold())
                .as("Extreme NA stacking should not drive fixation below floor")
                .isGreaterThanOrEqualTo(0.1);

        assertThat(defaults.moodBaseline().pleasure())
                .as("Pleasure should remain in valid range")
                .isGreaterThanOrEqualTo(-1.0);

        assertThat(defaults.cognitiveResourcePoolConfig().maxEffort())
                .as("maxEffort should stay above minimum")
                .isGreaterThanOrEqualTo(3);

        assertThat(defaults.capacityConfig().workingMemorySpan())
                .as("WM span should stay positive")
                .isGreaterThan(0.0);
    }
}
