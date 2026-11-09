package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognition.trace.PipelineTrace;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PipelineSnapshotStore {
    void store(String tenantId, String agentId, int tick, Instant capturedAt, PipelineTrace trace);
    List<PipelineSnapshot> find(String tenantId, String agentId, int fromTick, int toTick);
    Optional<PipelineSnapshot> findByTick(String tenantId, String agentId, int tick);
    List<PipelineSnapshot> findLatest(String tenantId, String agentId, int limit);
    long count(String tenantId);
    void purge(Instant olderThan);

    record PipelineSnapshot(String tenantId, String agentId, int tick, Instant capturedAt, PipelineTrace trace) {}
}
