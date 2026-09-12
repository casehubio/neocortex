package io.casehub.neocortex.memory.reflection.runtime;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.experience.ExperienceQuery;
import io.casehub.neocortex.memory.reflection.ReflectionEvents;
import io.casehub.neocortex.memory.reflection.ReflectionOrchestrator;
import io.casehub.neocortex.memory.reflection.ReflectionRecorded;
import io.casehub.neocortex.memory.reflection.ReflectionSynthesizer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ReflectionOrchestratorCore implements ReflectionOrchestrator {

    private final CaseMemoryStore store;
    private final ReflectionSynthesizer synthesizer;
    private final Consumer<ReflectionRecorded> recorded;

    public ReflectionOrchestratorCore(CaseMemoryStore store,
                                      ReflectionSynthesizer synthesizer,
                                      Consumer<ReflectionRecorded> recorded) {
        this.store = store;
        this.synthesizer = synthesizer;
        this.recorded = recorded;
    }

    @Override
    public List<String> reflect(String agentId, String tenantId, Instant since, int maxSourceMemories) {
        var query = ExperienceQuery.forAgent(agentId, tenantId);
        if (since != null) { query = query.withSince(since); }
        if (maxSourceMemories > 0) { query = query.withLimit(maxSourceMemories); }
        var sources = store.query(query);
        if (sources.isEmpty()) { return List.of(); }

        var reflections = synthesizer.synthesize(agentId, tenantId, sources, 1);
        if (reflections.isEmpty()) { return List.of(); }

        var memoryIds = new ArrayList<String>();
        for (var ref : reflections) {
            var input = ReflectionEvents.toMemoryInput(ref);
            var memoryId = store.store(input);
            recorded.accept(new ReflectionRecorded(ref, memoryId));
            memoryIds.add(memoryId);
        }
        return memoryIds;
    }
}
