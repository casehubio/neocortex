package io.casehub.neocortex.cognitive.observability;

import java.time.Instant;
import java.util.List;

public record GraphSnapshot(
    String snapshotId,
    String tenantId,
    String subgraphId,
    Instant capturedAt,
    SnapshotType type,
    List<NodeSnapshot> nodes,
    List<EdgeSnapshot> edges
) {
    public enum SnapshotType { KEYFRAME }
}
