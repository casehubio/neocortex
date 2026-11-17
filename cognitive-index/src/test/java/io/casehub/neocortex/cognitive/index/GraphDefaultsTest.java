package io.casehub.neocortex.cognitive.index;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GraphDefaultsTest {

    @Test
    void empty_allFieldsNull_servicesEmpty() {
        var empty = GraphDefaults.empty();
        assertThat(empty.vocabulary()).isNull();
        assertThat(empty.services()).isEmpty();
        assertThat(empty.traitRules()).isNull();
        assertThat(empty.derivedEdgeRules()).isNull();
        assertThat(empty.graphStructure()).isNull();
        assertThat(empty.extractionBias()).isNull();
    }

    @Test
    void mergeWith_explicitOnlyForNonDerived() {
        var derivedGraph = GraphStructureDefaults.defaults();
        var derivedExtraction = ExtractionBiasDefaults.defaults();

        var explicit = new GraphDefaults(null, Map.of("k", "v"), null, null, null, null);
        var derived = new GraphDefaults(null, Map.of(), null, null, derivedGraph, derivedExtraction);

        var merged = explicit.mergeWith(derived);
        assertThat(merged.services()).containsEntry("k", "v");
        assertThat(merged.graphStructure()).isSameAs(derivedGraph);
        assertThat(merged.extractionBias()).isSameAs(derivedExtraction);
    }

    @Test
    void services_defensivelyCopied() {
        var gd = new GraphDefaults(null, Map.of("a", "b"), null, null, null, null);
        assertThat(gd.services()).isUnmodifiable();
    }

    @Test
    void mergeWith_derivedNull_returnsThis() {
        var explicit = new GraphDefaults(null, Map.of("k", "v"), null, null, null, null);
        var merged = explicit.mergeWith(null);
        assertThat(merged).isSameAs(explicit);
    }
}
