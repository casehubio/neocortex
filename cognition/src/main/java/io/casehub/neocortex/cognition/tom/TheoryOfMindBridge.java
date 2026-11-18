package io.casehub.neocortex.cognition.tom;

import io.casehub.neocortex.cognition.appraisal.ActionReadiness;
import io.casehub.neocortex.cognition.appraisal.ActionTendency;
import io.casehub.neocortex.cognition.appraisal.AppraisalResult;
import io.casehub.neocortex.cognition.appraisal.SecDimensions;
import io.casehub.neocortex.cognition.mentalmodel.TheoryOfMindPosterior;
import io.casehub.neocortex.mindmap.intelligence.tom.TheoryOfMindEngine;
import io.casehub.neocortex.mindmap.intelligence.tom.TomPersonalityModulation;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps appraisal SEC dimensions and action tendencies into the
 * Bayesian Theory-of-Mind engine, returning an emotional-state
 * posterior for a subject.
 *
 * <p>Mapping grounded in Scherer’s SEC model:
 * <ul>
 *   <li>Threat level: derived from goal-incongruence (inverse of
 *       conduciveness) combined with low coping power (controllability).
 *   <li>Behavior type: derived from the intensity-weighted action
 *       tendency profile (approach vs. avoidant vs. neutral readiness).
 * </ul>
 *
 * <p>Both mappings produce soft evidence (probability distributions)
 * rather than hard assignments, preserving uncertainty through the
 * Bayesian inference.
 */
public class TheoryOfMindBridge {

    private static final Set<ActionReadiness> APPROACH_LIKE =
            EnumSet.of(ActionReadiness.APPROACH, ActionReadiness.DOMINANCE, ActionReadiness.ANTAGONISM);
    private static final Set<ActionReadiness> AVOIDANT_LIKE =
            EnumSet.of(ActionReadiness.AVOIDANCE, ActionReadiness.REJECTION, ActionReadiness.SUBMISSION);

    public TheoryOfMindBridge() {}

    public TheoryOfMindPosterior infer(AppraisalResult appraisal, TomPersonalityModulation modulation) {
        var engine = new TheoryOfMindEngine(modulation);
        engine.observeThreat(threatDistribution(appraisal.secDimensions()));
        double[] behavior = behaviorDistribution(appraisal.actionTendencies());
        if (modulation.suspiciousness() > 0.0) {
            behavior = scaleBehaviorEvidence(behavior, modulation.suspiciousness());
        }
        engine.observeBehavior(behavior);
        double[] posterior = engine.inferEmotionalState();
        return new TheoryOfMindPosterior(posterior[0], posterior[1], posterior[2]);
    }

    public TheoryOfMindPosterior infer(AppraisalResult appraisal) {
        return infer(appraisal, TomPersonalityModulation.NEUTRAL);
    }

    static double[] threatDistribution(Map<String, Double> secDimensions) {
        double conduciveness = secDimensions.getOrDefault(SecDimensions.CONDUCIVENESS, 0.0);
        double controllability = secDimensions.getOrDefault(SecDimensions.CONTROLLABILITY, 0.5);

        double incongruence = Math.clamp(-conduciveness, 0.0, 1.0);
        double powerlessness = Math.clamp(1.0 - controllability, 0.0, 1.0);
        double threatSignal = (incongruence + powerlessness) / 2.0;

        double severe = Math.clamp(threatSignal - 0.3, 0.0, 1.0) / 0.7;
        double none = Math.clamp(0.5 - threatSignal, 0.0, 1.0) / 0.5;
        double mild = 1.0 - severe - none;
        if (mild < 0) mild = 0;

        double sum = none + mild + severe;
        return new double[]{none / sum, mild / sum, severe / sum};
    }

    static double[] behaviorDistribution(List<ActionTendency> tendencies) {
        if (tendencies.isEmpty()) {
            return new double[]{0.33, 0.34, 0.33};
        }

        double approachWeight = 0;
        double avoidantWeight = 0;
        double neutralWeight = 0;

        for (var tendency : tendencies) {
            double w = tendency.intensity();
            if (APPROACH_LIKE.contains(tendency.readiness())) {
                approachWeight += w;
            } else if (AVOIDANT_LIKE.contains(tendency.readiness())) {
                avoidantWeight += w;
            } else {
                neutralWeight += w;
            }
        }

        double total = approachWeight + avoidantWeight + neutralWeight;
        if (total < 0.001) {
            return new double[]{0.33, 0.34, 0.33};
        }
        return new double[]{approachWeight / total, neutralWeight / total, avoidantWeight / total};
    }

    // Fenigstein & Vanable 1992; Combs et al. 2007 — attributional bias in suspicious individuals
    static double[] scaleBehaviorEvidence(double[] behavior, double suspiciousness) {
        double approach = behavior[0];
        double neutral  = behavior[1];
        double avoidant = behavior[2];

        double transfer = approach * suspiciousness * 0.6;
        approach -= transfer;
        avoidant += transfer;

        double sum = approach + neutral + avoidant;
        return new double[]{approach / sum, neutral / sum, avoidant / sum};
    }

}
