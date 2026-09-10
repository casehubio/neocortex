package io.casehub.neocortex.mindmap.intelligence;

import java.util.List;

public record SegmentationResult(List<String> createdNodeIds, int segmentCount) {
    public SegmentationResult {
        createdNodeIds = List.copyOf(createdNodeIds);
    }

    public static final SegmentationResult EMPTY = new SegmentationResult(List.of(), 0);
}
