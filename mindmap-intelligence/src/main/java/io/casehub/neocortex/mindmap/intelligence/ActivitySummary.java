package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.MindMapNode;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ActivitySummary(
        MindMapNode activityNode,
        MindMapNode placeNode,
        List<String> participantNames,
        Instant date
) {
    public ActivitySummary {
        Objects.requireNonNull(activityNode, "activityNode");
        participantNames = participantNames == null ? List.of() : List.copyOf(participantNames);
    }
}
