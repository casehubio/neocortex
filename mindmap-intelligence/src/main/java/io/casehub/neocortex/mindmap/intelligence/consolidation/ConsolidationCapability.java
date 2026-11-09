package io.casehub.neocortex.mindmap.intelligence.consolidation;

import java.util.Set;

public record ConsolidationCapability(Set<String> requiredNodeKinds, Set<String> producedTraits) {

    private static final ConsolidationCapability EMPTY = new ConsolidationCapability(Set.of(), Set.of());

    public static ConsolidationCapability empty() {
        return EMPTY;
    }
}
