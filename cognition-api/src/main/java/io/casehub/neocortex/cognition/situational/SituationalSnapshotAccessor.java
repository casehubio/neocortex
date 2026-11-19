package io.casehub.neocortex.cognition.situational;

import io.casehub.neocortex.cognition.trace.PipelineTrace.SituationalPersonalitySnapshot;

import java.util.Optional;

@FunctionalInterface
public interface SituationalSnapshotAccessor {
    Optional<SituationalPersonalitySnapshot> currentSnapshot(String agentId, String tenantId);
}
