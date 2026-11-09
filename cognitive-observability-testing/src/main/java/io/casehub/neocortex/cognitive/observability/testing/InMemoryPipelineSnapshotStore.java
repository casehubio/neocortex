package io.casehub.neocortex.cognitive.observability.testing;

import io.casehub.neocortex.cognitive.observability.PipelineSnapshotStore;
import io.casehub.neocortex.cognition.trace.PipelineTrace;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPipelineSnapshotStore implements PipelineSnapshotStore {

    private final Map<String, List<PipelineSnapshot>> snapshots = new ConcurrentHashMap<>();

    private String key(String tenantId, String agentId) {
        return tenantId + ":" + agentId;
    }

    @Override
    public void store(String tenantId, String agentId, int tick, Instant capturedAt, PipelineTrace trace) {
        snapshots.compute(key(tenantId, agentId), (k, list) -> {
            if (list == null) list = new ArrayList<>();
            list.removeIf(s -> s.tick() == tick);
            list.add(new PipelineSnapshot(tenantId, agentId, tick, capturedAt, trace));
            return list;
        });
    }

    @Override
    public List<PipelineSnapshot> find(String tenantId, String agentId, int fromTick, int toTick) {
        return snapshots.getOrDefault(key(tenantId, agentId), List.of()).stream()
                .filter(s -> s.tick() >= fromTick && s.tick() <= toTick)
                .sorted(Comparator.comparingInt(PipelineSnapshot::tick))
                .toList();
    }

    @Override
    public Optional<PipelineSnapshot> findByTick(String tenantId, String agentId, int tick) {
        return snapshots.getOrDefault(key(tenantId, agentId), List.of()).stream()
                .filter(s -> s.tick() == tick)
                .findFirst();
    }

    @Override
    public List<PipelineSnapshot> findLatest(String tenantId, String agentId, int limit) {
        return snapshots.getOrDefault(key(tenantId, agentId), List.of()).stream()
                .sorted(Comparator.comparingInt(PipelineSnapshot::tick).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public long count(String tenantId) {
        return snapshots.entrySet().stream()
                .filter(e -> e.getKey().startsWith(tenantId + ":"))
                .mapToLong(e -> e.getValue().size())
                .sum();
    }

    @Override
    public void purge(Instant olderThan) {
        snapshots.values().forEach(list -> list.removeIf(s -> s.capturedAt().isBefore(olderThan)));
        snapshots.entrySet().removeIf(e -> e.getValue().isEmpty());
    }
}
