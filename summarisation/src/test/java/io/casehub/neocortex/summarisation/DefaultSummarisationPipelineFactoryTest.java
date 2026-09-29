package io.casehub.neocortex.summarisation;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultSummarisationPipelineFactoryTest {

    @Test
    void createReturnsFunctionalPipeline() {
        var factory = new DefaultSummarisationPipelineFactory();
        StatefulSummariser<String, String, Void> summariser =
                (batch, prev) -> CompletableFuture.completedFuture(
                        new StatefulSummariser.SummariseResult<>(
                                batch.stream().map(LevelEvent::payload).toList(), null));

        var pipeline = factory.create(
                summariser, null,
                (buffered, state, now) -> buffered.size() >= 2,
                null, "test");

        pipeline.accept(new LevelEvent<>("a", 1L, new EventLevel("test", 0), null));
        pipeline.accept(new LevelEvent<>("b", 2L, new EventLevel("test", 0), null));
        var result = pipeline.tick(3L);
        assertThat(result).isNotNull();
    }
}
