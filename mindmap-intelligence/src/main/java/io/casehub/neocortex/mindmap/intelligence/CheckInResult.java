package io.casehub.neocortex.mindmap.intelligence;

import java.util.List;
import java.util.Objects;

public record CheckInResult(
        String activityNodeId,
        String placeNodeId,
        List<String> participantEdgeIds
) {
    public CheckInResult {
        Objects.requireNonNull(activityNodeId, "activityNodeId");
        Objects.requireNonNull(placeNodeId, "placeNodeId");
        participantEdgeIds = participantEdgeIds == null ? List.of() : List.copyOf(participantEdgeIds);
    }
}
