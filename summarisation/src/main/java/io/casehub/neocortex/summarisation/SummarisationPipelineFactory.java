package io.casehub.neocortex.summarisation;

import org.jspecify.annotations.Nullable;

public interface SummarisationPipelineFactory {

    <IN, OUT, S> SummarisationPipeline<IN> create(
            StatefulSummariser<IN, OUT, S> summariser,
            @Nullable OutputProcessor<OUT, S> outputProcessor,
            EmissionPolicy<IN, S> emissionPolicy,
            @Nullable StateStore<S> stateStore,
            String partitionKey);
}
