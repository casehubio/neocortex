package io.casehub.neocortex.mindmap.intelligence.consolidation;

import java.util.List;
import java.util.Set;

public record ConsolidationManifest(List<PhaseCapability> phases, String pipelineHash) {

    public record PhaseCapability(String name, Set<String> requiredNodeKinds, Set<String> producedTraits) {}
}
