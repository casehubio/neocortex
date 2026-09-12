package io.casehub.neocortex.cognitive.index;

import io.casehub.platform.api.identity.PrincipalId;

import java.util.Map;
import java.util.Set;

public record PerspectivalComparison(
        String entityId, String entityName,
        Map<PrincipalId, AffectSnapshot> perspectives,
        Set<PrincipalId> unassessedAgents,
        PadDistanceMatrix distances,
        Map<PadDimension, PairwiseDifferences> dimensionDifferences,
        TrajectoryAlignment trajectoryAlignment,
        int agentCount
) {}