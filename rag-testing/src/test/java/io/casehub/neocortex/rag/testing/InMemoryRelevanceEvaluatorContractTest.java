package io.casehub.neocortex.rag.testing;

import io.casehub.neocortex.rag.RelevanceEvaluator;

class InMemoryRelevanceEvaluatorContractTest extends RelevanceEvaluatorContractTest {
    @Override
    protected RelevanceEvaluator evaluator() {
        return new InMemoryRelevanceEvaluator();
    }
}
