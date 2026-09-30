package io.casehub.neocortex.cognition.narrative;

import io.casehub.neocortex.memory.ReflectionEntry;
import io.casehub.neocortex.memory.ReflectionQueryStore;
import io.casehub.neocortex.summarisation.EventLevel;
import io.casehub.neocortex.summarisation.LevelEventBus;
import io.casehub.neocortex.summarisation.SummarisationPipeline;
import io.casehub.neocortex.summarisation.SummarisationPipelineFactory;

public class NarrativePipeline {

    static final EventLevel REFLECTIONS = new EventLevel("reflections", 0);

    private final LevelEventBus<ReflectionEntry> reflectionBus;
    private final SummarisationPipeline<ReflectionEntry> pipeline;
    private final ReflectionEventAdapter adapter;

    public NarrativePipeline(
            NarrativeContentSummariser summariser,
            NarrativeConfig config,
            ReflectionQueryStore reflectionQueryStore,
            NarrativeMemory narrativeMemory,
            SummarisationPipelineFactory pipelineFactory) {
        this(summariser, config, reflectionQueryStore, narrativeMemory,
                pipelineFactory, new LevelEventBus<>());
    }

    NarrativePipeline(
            NarrativeContentSummariser summariser,
            NarrativeConfig config,
            ReflectionQueryStore reflectionQueryStore,
            NarrativeMemory narrativeMemory,
            SummarisationPipelineFactory pipelineFactory,
            LevelEventBus<ReflectionEntry> reflectionBus) {
        this.reflectionBus = reflectionBus;

        this.adapter = new ReflectionEventAdapter(
                reflectionQueryStore, reflectionBus, REFLECTIONS);

        var stateStore = new NarrativeStateStore(narrativeMemory);
        this.pipeline = pipelineFactory.create(
                summariser.asSummariser(),
                new NarrativeOutputProcessor(config),
                new NarrativeEmissionPolicy(config.synthesisGate()),
                stateStore,
                "narrative");

        reflectionBus.subscribe(e -> true, pipeline::accept);
    }

    public void tick(String agentId, String tenantId) {
        adapter.publishNewReflections(agentId, tenantId);
        pipeline.tick(System.currentTimeMillis());
    }
}
