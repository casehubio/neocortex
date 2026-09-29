package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.ConsolidationArtifact;

import java.util.List;

public record ConsolidationCompleted(
        String tenantId,
        List<PhaseResult> phaseResults,
        List<ConsolidationArtifact> artifacts) {}
