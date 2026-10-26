package io.casehub.neocortex.cognition.mood;

import io.casehub.neocortex.caps.SituationContext;

import java.util.Map;

/**
 * Maps CAPS input-node activations to PAD mood deltas mechanically,
 * replacing the LLM-based mood appraisal call (Scherer 2001 CPM).
 */
public final class MechanicalMoodAppraiser {

    private static final Map<String, double[]> PAD_MAP = Map.ofEntries(
        // {pleasure, arousal, dominance} — Scherer SEC → PAD
        // Relationship (Bowlby 1969)
        Map.entry("secure_attachment",    new double[]{ 0.20, -0.10,  0.05}),
        Map.entry("inconsistent_care",    new double[]{-0.10,  0.15, -0.10}),
        Map.entry("rejection",            new double[]{-0.20,  0.10, -0.15}),
        Map.entry("betrayal",             new double[]{-0.25,  0.20, -0.15}),
        Map.entry("abandonment",          new double[]{-0.20,  0.05, -0.20}),
        Map.entry("neglect",              new double[]{-0.15, -0.05, -0.10}),
        // Threat (Gray 1982, Dickerson & Kemeny 2004)
        Map.entry("physical_threat",      new double[]{-0.15,  0.25, -0.10}),
        Map.entry("social_threat",        new double[]{-0.15,  0.20, -0.15}),
        Map.entry("psychological_threat", new double[]{-0.15,  0.15, -0.20}),
        Map.entry("unpredictable_danger", new double[]{-0.10,  0.25, -0.15}),
        // Achievement (McClelland 1961)
        Map.entry("mastery",              new double[]{ 0.20,  0.10,  0.20}),
        Map.entry("failure",              new double[]{-0.20,  0.10, -0.20}),
        Map.entry("performance_judged",   new double[]{-0.05,  0.15, -0.10}),
        Map.entry("competence_recognition", new double[]{ 0.20,  0.10,  0.15}),
        // Social (Baumeister & Leary 1995)
        Map.entry("acceptance",           new double[]{ 0.20, -0.05,  0.05}),
        Map.entry("exclusion",            new double[]{-0.20,  0.10, -0.15}),
        Map.entry("dominance",            new double[]{ 0.05,  0.15,  0.20}),
        Map.entry("submission",           new double[]{-0.10, -0.05, -0.20}),
        Map.entry("modeling_observed",    new double[]{ 0.05,  0.05,  0.00}),
        // Resource (Hobfoll 1989)
        Map.entry("scarcity",             new double[]{-0.15,  0.10, -0.10}),
        Map.entry("abundance",            new double[]{ 0.15, -0.05,  0.10}),
        Map.entry("competition",          new double[]{ 0.05,  0.20,  0.05}),
        Map.entry("sharing",              new double[]{ 0.15, -0.05,  0.05}),
        // Autonomy (Deci & Ryan 2000)
        Map.entry("agency_granted",       new double[]{ 0.15,  0.05,  0.25}),
        Map.entry("controlled",           new double[]{-0.10,  0.10, -0.25}),
        Map.entry("choice_available",     new double[]{ 0.10,  0.05,  0.15}),
        Map.entry("powerlessness",        new double[]{-0.15,  0.10, -0.25}),
        // Consequence (Gray 1982, Skinner 1953)
        Map.entry("reward",               new double[]{ 0.25,  0.10,  0.10}),
        Map.entry("punishment",           new double[]{-0.20,  0.15, -0.15}),
        Map.entry("reward_removal",       new double[]{-0.20,  0.10, -0.10}),
        Map.entry("aversive_removal",     new double[]{ 0.15, -0.10,  0.10})
    );

    public static MoodSignal.InteractionAppraisal appraise(SituationContext situation) {
        if (situation == null || situation.isEmpty()) {
            return null;
        }
        double p = 0, a = 0, d = 0;
        for (var activation : situation.activations()) {
            double[] pad = PAD_MAP.get(activation.nodeId());
            if (pad == null) continue;
            double c = activation.confidence();
            p += pad[0] * c;
            a += pad[1] * c;
            d += pad[2] * c;
        }
        p = Math.clamp(p, -0.3, 0.3);
        a = Math.clamp(a, -0.3, 0.3);
        d = Math.clamp(d, -0.3, 0.3);
        if (Math.abs(p) < 0.01 && Math.abs(a) < 0.01 && Math.abs(d) < 0.01) {
            return null;
        }
        return new MoodSignal.InteractionAppraisal(p, a, d, "mechanical-appraisal");
    }

    private MechanicalMoodAppraiser() {}
}
