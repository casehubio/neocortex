package io.casehub.neocortex.mindmap.intelligence.consolidation;

import java.util.List;

public record ConsolidationCompleted(String tenantId, List<PhaseResult> phaseResults) {}
