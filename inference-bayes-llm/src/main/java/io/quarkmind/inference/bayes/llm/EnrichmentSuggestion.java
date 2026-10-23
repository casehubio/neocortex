package io.quarkmind.inference.bayes.llm;

import io.quarkmind.inference.bayes.EdgeSpec;
import io.quarkmind.inference.bayes.VariableSpec;

public sealed interface EnrichmentSuggestion {
    record SuggestedVariable(VariableSpec variable, String justification)
        implements EnrichmentSuggestion {}
    record SuggestedEdge(EdgeSpec edge, String justification)
        implements EnrichmentSuggestion {}
}
