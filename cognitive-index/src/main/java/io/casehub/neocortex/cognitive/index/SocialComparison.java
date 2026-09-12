package io.casehub.neocortex.cognitive.index;

import io.casehub.platform.api.identity.PrincipalId;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SocialComparison {
    private SocialComparison() {}

    public static PerspectivalComparison compare(
            Map<PrincipalId, EntityKnowledge> perspectives) {
        if (perspectives.isEmpty()) {
            throw new IllegalArgumentException("at least one perspective required");
        }

        EntityKnowledge first      = perspectives.values().iterator().next();
        String          entityId   = first.node().id();
        String          entityName = first.node().name();

        Map<PrincipalId, AffectSnapshot> snapshots  = new LinkedHashMap<>();
        Set<PrincipalId>                 unassessed = new LinkedHashSet<>();

        for (var entry : perspectives.entrySet()) {
            PrincipalId     agent = entry.getKey();
            EntityKnowledge ek    = entry.getValue();
            Double          p     = ek.node().pleasure();
            Double          a     = ek.node().arousal();
            Double          d     = ek.node().dominance();
            if (p == null || a == null || d == null) {
                unassessed.add(agent);
            }
            snapshots.put(agent, new AffectSnapshot(agent, p, a, d, ek.trajectory()));
        }

        List<PrincipalId> assessed = perspectives.keySet().stream()
                                                 .filter(a -> !unassessed.contains(a)).toList();
        List<PrincipalId> allAgents = List.copyOf(perspectives.keySet());

        PadDistanceMatrix                      distances = computeDistances(snapshots, assessed);
        Map<PadDimension, PairwiseDifferences> diffs     = computeDifferences(snapshots, assessed);
        TrajectoryAlignment                    alignment = computeAlignment(perspectives, allAgents);

        return new PerspectivalComparison(entityId, entityName, snapshots,
                                          unassessed, distances, diffs, alignment, perspectives.size());
    }

    private static PadDistanceMatrix computeDistances(
            Map<PrincipalId, AffectSnapshot> snapshots, List<PrincipalId> assessed) {
        Map<AgentPair, Double> distances = new LinkedHashMap<>();
        for (int i = 0; i < assessed.size(); i++) {
            for (int j = i + 1; j < assessed.size(); j++) {
                AffectSnapshot a  = snapshots.get(assessed.get(i));
                AffectSnapshot b  = snapshots.get(assessed.get(j));
                double         dp = a.pleasure() - b.pleasure();
                double         da = a.arousal() - b.arousal();
                double         dd = a.dominance() - b.dominance();
                distances.put(AgentPair.of(assessed.get(i), assessed.get(j)),
                              Math.sqrt(dp * dp + da * da + dd * dd));
            }
        }
        return new PadDistanceMatrix(distances);
    }

    private static Map<PadDimension, PairwiseDifferences> computeDifferences(
            Map<PrincipalId, AffectSnapshot> snapshots, List<PrincipalId> assessed) {
        Map<PadDimension, PairwiseDifferences> result = new LinkedHashMap<>();
        for (PadDimension dim : PadDimension.values()) {
            Map<AgentPair, Double> diffs = new LinkedHashMap<>();
            for (int i = 0; i < assessed.size(); i++) {
                for (int j = i + 1; j < assessed.size(); j++) {
                    AgentPair      pair = AgentPair.of(assessed.get(i), assessed.get(j));
                    AffectSnapshot sa   = snapshots.get(pair.a());
                    AffectSnapshot sb   = snapshots.get(pair.b());
                    diffs.put(pair, padDim(sa, dim) - padDim(sb, dim));
                }
            }
            result.put(dim, new PairwiseDifferences(diffs));
        }
        return result;
    }

    private static TrajectoryAlignment computeAlignment(
            Map<PrincipalId, EntityKnowledge> perspectives, List<PrincipalId> assessed) {
        Map<AgentPair, Double>         cosines    = new LinkedHashMap<>();
        Map<AgentPair, TrendAgreement> agreements = new LinkedHashMap<>();

        for (int i = 0; i < assessed.size(); i++) {
            for (int j = i + 1; j < assessed.size(); j++) {
                PrincipalId      ai   = assessed.get(i);
                PrincipalId      aj   = assessed.get(j);
                AgentPair        pair = AgentPair.of(ai, aj);
                AffectTrajectory ta   = perspectives.get(ai).trajectory();
                AffectTrajectory tb   = perspectives.get(aj).trajectory();

                if (ta == null || tb == null || ta.sampleCount() < 2 || tb.sampleCount() < 2) {
                    cosines.put(pair, 0.0);
                    agreements.put(pair, TrendAgreement.INSUFFICIENT);
                    continue;
                }

                double[] va   = {ta.pleasureSlope(), ta.arousalSlope(), ta.dominanceSlope()};
                double[] vb   = {tb.pleasureSlope(), tb.arousalSlope(), tb.dominanceSlope()};
                double   dot  = va[0] * vb[0] + va[1] * vb[1] + va[2] * vb[2];
                double   magA = Math.sqrt(va[0] * va[0] + va[1] * va[1] + va[2] * va[2]);
                double   magB = Math.sqrt(vb[0] * vb[0] + vb[1] * vb[1] + vb[2] * vb[2]);

                double cosine = (magA < 1e-9 || magB < 1e-9) ? 0.0 : dot / (magA * magB);
                cosines.put(pair, cosine);

                TrendAgreement agreement;
                if (ta.trend() == tb.trend()) {
                    agreement = TrendAgreement.ALIGNED;
                } else if (ta.trend() == TrendDirection.STABLE
                           || tb.trend() == TrendDirection.STABLE) {
                    agreement = TrendAgreement.MIXED;
                } else {
                    agreement = TrendAgreement.DIVERGENT;
                }
                agreements.put(pair, agreement);
            }
        }
        return new TrajectoryAlignment(cosines, agreements);
    }

    private static double padDim(AffectSnapshot s, PadDimension dim) {
        return switch (dim) {
            case PLEASURE -> s.pleasure();
            case AROUSAL -> s.arousal();
            case DOMINANCE -> s.dominance();
        };
    }
}