package io.casehub.neocortex.cognition.drive;

import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.SubgraphTypes;

public class MindMapDriveSource implements DriveSource {

    private static final String DRIVE_INTENSITY_KIND = "drive-intensity";

    private final DriveAxis axis;
    private final MindMapStore mindMapStore;
    private final CognitiveDefaultsRegistry registry;

    public MindMapDriveSource(DriveAxis axis, MindMapStore mindMapStore, CognitiveDefaultsRegistry registry) {
        this.axis = axis;
        this.mindMapStore = mindMapStore;
        this.registry = registry;
    }

    @Override
    public DriveIntensity evaluate(String agentId, String tenantId) {
        var nodes = mindMapStore.search(MindMapQuery.of(tenantId, 200).withType(SubgraphTypes.COGNITIVE));
        for (var node : nodes) {
            if (!DRIVE_INTENSITY_KIND.equals(node.properties().get("cognitiveKind"))) continue;
            if (!agentId.equals(node.properties().get("agent-id"))) continue;
            if (!axis.name().equals(node.properties().get("drive-type"))) continue;
            double intensity = parseDouble(node.properties().get("intensity"), 0.4);
            String description = node.properties().getOrDefault("description", axis.name().toLowerCase());
            return new DriveIntensity(axis, Math.clamp(intensity, 0.0, 1.0), description);
        }
        return personalityFallback(agentId);
    }

    private DriveIntensity personalityFallback(String agentId) {
        var defaults = registry.forAgent(agentId);
        if (defaults.isPresent() && defaults.get().descriptor() != null) {
            var axes = defaults.get().descriptor().disposition();
            if (axes != null) {
                double baseline = computeBaseline(axes);
                return new DriveIntensity(axis, baseline, axis.name().toLowerCase());
            }
        }
        return new DriveIntensity(axis, 0.4, axis.name().toLowerCase());
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
        if (value == null) return 0.4;
        return switch (value.toLowerCase()) {
            case "cooperative", "strict", "aggressive", "high", "autonomous", "confronting", "competitive" -> 0.6;
            case "moderate", "collaborative", "analytical", "accommodating" -> 0.4;
            case "independent", "flexible", "conservative", "low", "avoidant" -> 0.3;
            default -> 0.4;
        };
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null) return fallback;
        try { return Double.parseDouble(value); }
        catch (NumberFormatException e) { return fallback; }
    }
}
