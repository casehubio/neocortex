package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.observability.DerivationTrace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DerivationTraceTest {

    private static final DispositionAxes DEFAULT_AXES =
            new DispositionAxes("cooperative", "moderate", "calculated", "moderate", "analytical");

    private static final List<WeightedTerm> BALANCED_PROFILE = List.of(
            new WeightedTerm("ni", 0.3), new WeightedTerm("te", 0.25),
            new WeightedTerm("fi", 0.25), new WeightedTerm("se", 0.2));

    @Test
    void trace_capturesModulationEvents() {
        var facets = List.of(
                new PersonalityFacet(PersonalityFacet.IMPULSIVITY, 0.9, "test"),
                new PersonalityFacet(PersonalityFacet.CALLOUSNESS, 0.8, "test"),
                new PersonalityFacet(PersonalityFacet.ANXIOUSNESS, 0.2, "test"));
        var descriptor = new DescriptorView("traced-agent", DEFAULT_AXES, BALANCED_PROFILE,
                List.of("investigate"), null, facets);

        var trace = new DerivationTrace();
        CognitiveDerivationEngine.derive(descriptor, trace);

        assertThat(trace.isEmpty()).isFalse();
        assertThat(trace.modulations()).isNotEmpty();

        var impulsivityMods = trace.modulationsFor(PersonalityFacet.IMPULSIVITY);
        assertThat(impulsivityMods).isNotEmpty();
        assertThat(impulsivityMods.stream().anyMatch(m -> "workingMemorySpan".equals(m.field()))).isTrue();

        var callousnessMods = trace.modulationsFor(PersonalityFacet.CALLOUSNESS);
        assertThat(callousnessMods).isNotEmpty();
        assertThat(callousnessMods.stream().anyMatch(m -> "relationshipBias".equals(m.field()))).isTrue();
    }

    @Test
    void trace_capturesStageCompletionEvents() {
        var facets = List.of(new PersonalityFacet(PersonalityFacet.RISK_TAKING, 0.8, "test"));
        var descriptor = new DescriptorView("traced-agent", DEFAULT_AXES, BALANCED_PROFILE,
                List.of(), null, facets);

        var trace = new DerivationTrace();
        CognitiveDerivationEngine.derive(descriptor, trace);

        var stages = trace.events().stream()
                .filter(e -> e instanceof DerivationTrace.StageCompleted)
                .map(e -> ((DerivationTrace.StageCompleted) e).method())
                .toList();

        assertThat(stages).contains(
                "deriveMoodBaseline", "deriveCuriosity", "deriveCbrStrategy",
                "deriveSocialCognition", "deriveGraphStructure", "deriveExtractionBias",
                "deriveAppraisalWeights", "deriveHabituationConfig", "deriveRegulationDefaults",
                "deriveCapacity");
    }

    @Test
    void trace_noFacetsMeansNoModulations() {
        var descriptor = DescriptorView.of("no-facets", DEFAULT_AXES, BALANCED_PROFILE, List.of());

        var trace = new DerivationTrace();
        CognitiveDerivationEngine.derive(descriptor, trace);

        assertThat(trace.modulations()).isEmpty();
    }

    @Test
    void trace_queryByMethod() {
        var facets = List.of(new PersonalityFacet(PersonalityFacet.WITHDRAWAL, 0.9, "test"));
        var descriptor = new DescriptorView("traced-agent", DEFAULT_AXES, BALANCED_PROFILE,
                List.of(), null, facets);

        var trace = new DerivationTrace();
        CognitiveDerivationEngine.derive(descriptor, trace);

        var graphMods = trace.modulationsIn("deriveGraphStructure");
        assertThat(graphMods).hasSize(1);
        assertThat(graphMods.getFirst().facet()).isEqualTo(PersonalityFacet.WITHDRAWAL);
        assertThat(graphMods.getFirst().field()).isEqualTo("connectiveBias");
        assertThat(graphMods.getFirst().delta()).isLessThan(0.0);
    }
}
