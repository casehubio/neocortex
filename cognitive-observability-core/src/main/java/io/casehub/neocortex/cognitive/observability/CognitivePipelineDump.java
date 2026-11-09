package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognition.trace.PipelineTrace;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record CognitivePipelineDump(
        String tenantId,
        String agentId,
        QueryMode queryMode,
        @Nullable PipelineTrace liveTrace,
        List<PipelineSnapshotStore.PipelineSnapshot> snapshots
) {
    public enum QueryMode { LIVE, HISTORICAL_SINGLE, HISTORICAL_RANGE }

    public static CognitivePipelineDump live(String tenantId, String agentId, @Nullable PipelineTrace trace) {
        return new CognitivePipelineDump(tenantId, agentId, QueryMode.LIVE, trace, List.of());
    }

    public static CognitivePipelineDump single(String tenantId, String agentId,
                                               PipelineSnapshotStore.@Nullable PipelineSnapshot snapshot) {
        return new CognitivePipelineDump(tenantId, agentId, QueryMode.HISTORICAL_SINGLE, null,
                snapshot != null ? List.of(snapshot) : List.of());
    }

    public static CognitivePipelineDump range(String tenantId, String agentId,
                                              List<PipelineSnapshotStore.PipelineSnapshot> snapshots) {
        return new CognitivePipelineDump(tenantId, agentId, QueryMode.HISTORICAL_RANGE, null, snapshots);
    }
}
