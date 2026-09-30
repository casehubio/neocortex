package io.casehub.neocortex.summarisation;

public interface SummarisationPipeline<IN> extends Tickable {
    void accept(LevelEvent<IN> event);
}
