package io.casehub.neocortex.summarisation;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;

@ApplicationScoped
@DefaultBean
public class DefaultSummarisationPipelineFactory implements SummarisationPipelineFactory {

    @Override
    public <IN, OUT, S> SummarisationPipeline<IN> create(
            StatefulSummariser<IN, OUT, S> summariser,
            @Nullable OutputProcessor<OUT, S> outputProcessor,
            EmissionPolicy<IN, S> emissionPolicy,
            @Nullable StateStore<S> stateStore,
            String partitionKey) {

        var outputBus = new LevelEventBus<OUT>();
        var outputLevel = new EventLevel("summarised", 1);
        var builder = SummarisationRunner.<IN, OUT>builder(summariser, outputBus, outputLevel)
                .emissionPolicy(emissionPolicy);

        if (outputProcessor != null) {
            builder.outputProcessor(outputProcessor);
        }
        if (stateStore != null) {
            builder.stateStore(stateStore);
        }

        var runner = builder.build();
        return new SummarisationPipeline<IN>() {
            @Override
            public void accept(LevelEvent<IN> event) {
                runner.collect(event);
            }

            @Override
            public CompletionStage<Void> tick(long now) {
                return runner.tick(now);
            }

            @Override
            public CompletionStage<Void> flush() {
                return runner.flush();
            }
        };
    }
}
