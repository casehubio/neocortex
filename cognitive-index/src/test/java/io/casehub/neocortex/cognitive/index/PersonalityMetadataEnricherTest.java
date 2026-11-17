package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.mindmap.AppraisalWeights;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalityMetadataEnricherTest {

    private static final String AGENT_ID = "test-agent";

    @Test
    void nullRegistry_returnsOriginalMetadata() {
        var original = Map.of("key", "val");
        var result = PersonalityMetadataEnricher.enrich(original, AGENT_ID, null);
        assertThat(result).isSameAs(original);
    }

    @Test
    void noDefaults_returnsOriginalMetadata() {
        var registry = CognitiveDefaultsRegistry.forTesting();
        var original = Map.of("key", "val");
        var result = PersonalityMetadataEnricher.enrich(original, AGENT_ID, registry);
        assertThat(result).isSameAs(original);
    }

    @Test
    void enrichesDisposition() {
        var defaults = buildDefaults(new DispositionAxes("cooperative", "high", "low", "moderate", "avoid"), List.of(), null);
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);

        var result = PersonalityMetadataEnricher.enrich(Map.of(), AGENT_ID, registry);

        assertThat(result).containsEntry("disposition.socialOrient", "cooperative");
        assertThat(result).containsEntry("disposition.ruleFollowing", "high");
        assertThat(result).containsEntry("disposition.riskAppetite", "low");
        assertThat(result).containsEntry("disposition.autonomy", "moderate");
        assertThat(result).containsEntry("disposition.conflictMode", "avoid");
    }

    @Test
    void enrichesPid5Facets() {
        var facets = List.of(
                new PersonalityFacet("Anxiousness", 0.8, "derived"),
                new PersonalityFacet("Withdrawal", 0.3, "derived"));
        var defaults = buildDefaults(null, facets, null);
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);

        var result = PersonalityMetadataEnricher.enrich(Map.of(), AGENT_ID, registry);

        assertThat(result).containsEntry("pid5.Anxiousness", "0.80");
        assertThat(result).containsEntry("pid5.Withdrawal", "0.30");
    }

    @Test
    void enrichesAppraisalWeights() {
        var aw = new AppraisalWeights(0.7, 0.6, 0.4, 0.8, 0.5);
        var defaults = buildDefaults(null, List.of(), aw);
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);

        var result = PersonalityMetadataEnricher.enrich(Map.of(), AGENT_ID, registry);

        assertThat(result).containsEntry("appraisal.urgencyWeight", "0.70");
        assertThat(result).containsEntry("appraisal.relationshipWeight", "0.60");
        assertThat(result).containsEntry("appraisal.fearOnsetThreshold", "0.40");
        assertThat(result).containsEntry("appraisal.selfStandardsStrictness", "0.80");
        assertThat(result).containsEntry("appraisal.otherStandardsStrictness", "0.50");
    }

    @Test
    void allowedFacets_filtersToConfiguredSet() {
        var facets = List.of(
                new PersonalityFacet("Anxiousness", 0.8, "derived"),
                new PersonalityFacet("Withdrawal", 0.3, "derived"),
                new PersonalityFacet("Hostility", 0.5, "derived"));
        var defaults = buildDefaults(null, facets, null);
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);

        var result = PersonalityMetadataEnricher.enrich(
                Map.of(), AGENT_ID, registry, Set.of("Anxiousness", "Hostility"));

        assertThat(result).containsEntry("pid5.Anxiousness", "0.80");
        assertThat(result).containsEntry("pid5.Hostility", "0.50");
        assertThat(result).doesNotContainKey("pid5.Withdrawal");
    }

    @Test
    void nullAllowedFacets_includesAll() {
        var facets = List.of(
                new PersonalityFacet("Anxiousness", 0.8, "derived"),
                new PersonalityFacet("Withdrawal", 0.3, "derived"));
        var defaults = buildDefaults(null, facets, null);
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);

        var result = PersonalityMetadataEnricher.enrich(Map.of(), AGENT_ID, registry, null);

        assertThat(result).containsEntry("pid5.Anxiousness", "0.80");
        assertThat(result).containsEntry("pid5.Withdrawal", "0.30");
    }

    @Test
    void preservesExistingMetadata() {
        var defaults = buildDefaults(new DispositionAxes("cooperative", null, null, null, null), List.of(), null);
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);
        var original = Map.of("event-type", "observation", "cognitiveKind", "affect-observation");

        var result = PersonalityMetadataEnricher.enrich(original, AGENT_ID, registry);

        assertThat(result).containsEntry("event-type", "observation");
        assertThat(result).containsEntry("cognitiveKind", "affect-observation");
        assertThat(result).containsEntry("disposition.socialOrient", "cooperative");
    }

    private static CognitiveDefaults buildDefaults(DispositionAxes axes,
                                                   List<PersonalityFacet> facets,
                                                   AppraisalWeights aw) {
        var descriptor = new DescriptorView(AGENT_ID, axes, List.of(), List.of(), null, facets);
        var defaults   = CognitiveDefaults.empty(AGENT_ID).withDescriptor(descriptor);
        if (aw != null) {
            defaults = defaults.withAppraisalWeights(aw);
        }
        return defaults;
    }
}
