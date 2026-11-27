package io.casehub.neocortex.cognition.drive;

import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.SubgraphTypes;

public class MindMapDriveSource implements DriveSource {

    private static final String DRIVE_INTENSITY_KIND = "drive-intensity";

    private final DriveAxis                                                       axis;
    private final MindMapStore                                                    mindMapStore;
    private final io.casehub.neocortex.cognitive.index.EffectiveCognitiveDefaults effectiveDefaults;

    public MindMapDriveSource(DriveAxis axis, MindMapStore mindMapStore, io.casehub.neocortex.cognitive.index.EffectiveCognitiveDefaults effectiveDefaults) {
        this.axis              = axis;
        this.mindMapStore      = mindMapStore;
        this.effectiveDefaults = effectiveDefaults;
    }

    @Override
    public DriveIntensity evaluate(String agentId, String tenantId) {
        var nodes = mindMapStore.search(MindMapQuery.of(tenantId, 2000).withType(SubgraphTypes.COGNITIVE));
        for (var node : nodes) {
            if (!DRIVE_INTENSITY_KIND.equals(node.properties().get("cognitiveKind"))) {continue;}
            if (!agentId.equals(node.properties().get("agent-id"))) {continue;}
            if (!axis.name().equals(node.properties().get("drive-type"))) {continue;}
            double intensity   = parseDouble(node.properties().get("intensity"), 0.4);
            String description = node.properties().getOrDefault("description", axis.name().toLowerCase());
            return new DriveIntensity(axis, Math.clamp(intensity, 0.0, 1.0), description);
        }
        return personalityFallback(agentId, tenantId);
    }

    private DriveIntensity personalityFallback(String agentId, String tenantId) {
        var defaults = effectiveDefaults.forAgent(tenantId, agentId);
        if (defaults != null && defaults.descriptor() != null) {
            var axes = defaults.descriptor().disposition();
            if (axes != null) {
                double baseline = computeBaseline(axes);
                String trigger  = describeTrigger(axes);
                return new DriveIntensity(axis, baseline, trigger);
            }
        }
        return new DriveIntensity(axis, 0.4, axis.name().toLowerCase());
    }

    private String describeTrigger(DispositionAxes axes) {
        return switch (axis) {
            case COMPETENCE -> axes.ruleFollowing() + " rule-follower, " + axes.riskAppetite() + " risk appetite — mastery orientation";
            case AFFILIATION -> axes.socialOrient() + " social orientation, " + axes.conflictMode() + " in conflict — social connection";
            case AUTONOMY -> axes.autonomy() + " autonomy — self-determination";
            case CURIOSITY -> "baseline curiosity";
        };
    }

    private double computeBaseline(DispositionAxes axes) {
        return switch (axis) {
            case COMPETENCE -> (axisValue(axes.ruleFollowing()) + axisValue(axes.riskAppetite())) / 2.0;
            case AFFILIATION -> (axisValue(axes.socialOrient()) + axisValue(axes.conflictMode())) / 2.0;
            case AUTONOMY -> axisValue(axes.autonomy());
            case CURIOSITY -> 0.4;
        };
    }

    private static double axisValue(String value) {
        if (value == null) {return 0.5;}
        return switch (value.toLowerCase()) {
            case "collaborative", "strict", "aggressive", "autonomous", "confronting",
                 "competing", "collaborating", "bold", "assertive", "dominant",
                 "proactive", "driven", "enthusiastic" -> 0.6;
            case "principled", "semi-autonomous", "measured", "compromising",
                 "accommodating", "moderate", "balanced", "pragmatic", "adaptive" -> 0.5;
            case "independent", "flexible", "conservative", "directed", "avoiding",
                 "cautious", "reserved", "passive", "compliant", "deferential" -> 0.3;
            default -> {
                System.getLogger(MindMapDriveSource.class.getName()).log(
                        System.Logger.Level.DEBUG,
                        "Unrecognised disposition term ''{0}'' — using neutral baseline",
                        value);
                yield 0.5;
            }
        };
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null) {return fallback;}
        try {return Double.parseDouble(value);} catch (NumberFormatException e) {return fallback;}
    }
}
