package io.casehub.neocortex.mindmap.intelligence;

import java.util.List;
import java.util.Objects;

public record CheckInResult(
        String activityNodeId,
        String placeNodeId,
        List<String> participantEdgeIds,
        String memoryId
) {
    public CheckInResult {
        Objects.requireNonNull(activityNodeId, "activityNodeId");
        Objects.requireNonNull(placeNodeId, "placeNodeId");
        participantEdgeIds = participantEdgeIds == null ? List.of() : List.copyOf(participantEdgeIds);
    }

    public CheckInResult(String activityNodeId, String placeNodeId, List<String> participantEdgeIds) {
        this(activityNodeId, placeNodeId, participantEdgeIds, null);
    }
}
