package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.mindmap.DeclarativeDerivedEdgeRule;
import io.casehub.neocortex.mindmap.DeclarativeTraitRule;
import io.casehub.neocortex.mindmap.MindMapVocabulary;

import java.util.List;
import java.util.Map;

public record GraphDefaults(
        MindMapVocabulary vocabulary,
        Map<String, String> services,
        List<DeclarativeTraitRule> traitRules,
        List<DeclarativeDerivedEdgeRule> derivedEdgeRules,
        GraphStructureDefaults graphStructure,
        ExtractionBiasDefaults extractionBias
) {
    public GraphDefaults {
        services = services != null ? Map.copyOf(services) : Map.of();
        traitRules = traitRules != null ? List.copyOf(traitRules) : null;
        derivedEdgeRules = derivedEdgeRules != null ? List.copyOf(derivedEdgeRules) : null;
    }

    public static GraphDefaults empty() {
        return new GraphDefaults(null, Map.of(), null, null, null, null);
    }

    public GraphDefaults mergeWith(GraphDefaults derived) {
        if (derived == null) return this;
        return new GraphDefaults(
                vocabulary,
                services,
                traitRules,
                derivedEdgeRules,
                graphStructure != null ? graphStructure : derived.graphStructure(),
                extractionBias != null ? extractionBias : derived.extractionBias());
    }
}
