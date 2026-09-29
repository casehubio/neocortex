package io.casehub.neocortex.cognition.narrative;

import io.casehub.neocortex.memory.ReflectionEntry;
import io.casehub.neocortex.memory.ReflectionQueryStore;
import io.casehub.neocortex.summarisation.EventLevel;
import io.casehub.neocortex.summarisation.LevelEventBus;
import io.casehub.neocortex.summarisation.LevelEvent;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class ReflectionEventAdapter {

    private final ReflectionQueryStore reflectionQueryStore;
    private final LevelEventBus<ReflectionEntry> outputBus;
    private final EventLevel outputLevel;
    private final ConcurrentHashMap<String, Instant> watermarks =
            new ConcurrentHashMap<>();

    public ReflectionEventAdapter(
            ReflectionQueryStore reflectionQueryStore,
            LevelEventBus<ReflectionEntry> outputBus,
            EventLevel outputLevel) {
        this.reflectionQueryStore = reflectionQueryStore;
        this.outputBus = outputBus;
        this.outputLevel = outputLevel;
    }

    public void publishNewReflections(String agentId, String tenantId) {
        var key = agentId + ":" + tenantId;
        var since = watermarks.getOrDefault(key, Instant.EPOCH);
        var reflections = reflectionQueryStore.findSince(
                agentId, tenantId, since);
        if (reflections.isEmpty()) return;

        for (var r : reflections) {
            outputBus.publish(new LevelEvent<>(
                    r, r.generatedAt().toEpochMilli(),
                    outputLevel, tenantId));
        }

        var latest = reflections.getLast().generatedAt();
        watermarks.put(key, latest);
    }
}
