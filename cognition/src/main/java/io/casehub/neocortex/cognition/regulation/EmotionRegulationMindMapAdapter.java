package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public final class EmotionRegulationMindMapAdapter {

    static final String COGNITIVE_KIND = "emotion-regulation-result";

    private EmotionRegulationMindMapAdapter() {}

    public static void write(MindMapStore store, String agentId, String tenantId,
                             String cognitiveSubgraphId, EmotionRegulationResult result) {
        write(store, agentId, tenantId, cognitiveSubgraphId, result, null);
    }

    public static void write(MindMapStore store, String agentId, String tenantId,
                             String cognitiveSubgraphId, EmotionRegulationResult result,
                             @org.jspecify.annotations.Nullable String dominantEmotionType) {
        write(store, agentId, tenantId, cognitiveSubgraphId, result, dominantEmotionType, null);
    }

    public static void write(MindMapStore store, String agentId, String tenantId,
                             String cognitiveSubgraphId, EmotionRegulationResult result,
                             @org.jspecify.annotations.Nullable String dominantEmotionType,
                             @org.jspecify.annotations.Nullable RegulationContext regulationContext) {
        write(store, agentId, tenantId, cognitiveSubgraphId, result, dominantEmotionType, regulationContext, null);
    }

    public static void write(MindMapStore store, String agentId, String tenantId,
                             String cognitiveSubgraphId, EmotionRegulationResult result,
                             @org.jspecify.annotations.Nullable String dominantEmotionType,
                             @org.jspecify.annotations.Nullable RegulationContext regulationContext,
                             @org.jspecify.annotations.Nullable String strategySource) {
        var props = new HashMap<String, String>();
        props.put("cognitiveKind", COGNITIVE_KIND);
        props.put("agent-id", agentId);
        props.put("timestamp", Instant.now().toString());
        props.put("strategy", result.strategy().name());
        props.put("targetEmotions", String.join(",", result.targetEmotions()));
        props.put("targetDrives", String.join(",", result.targetDrives()));
        props.put("peakOriginalIntensity", String.valueOf(result.peakOriginalIntensity()));
        props.put("regulatedIntensity", String.valueOf(result.regulatedIntensity()));
        props.put("effectiveness", String.valueOf(result.effectiveness()));
        props.put("narrative", result.narrative());
        if (result.regulationAxis() != null) {
            props.put("regulationAxis", result.regulationAxis().name());
        }
        if (result.environmentalDirective() != null) {
            props.put("environmentalDirective", result.environmentalDirective());
        }
        if (dominantEmotionType != null) {
            props.put("dominantEmotionType", dominantEmotionType);
        }
        if (strategySource != null) {
            props.put("strategySource", strategySource);
        }
        if (regulationContext != null) {
            if (regulationContext.location() != null) {
                props.put("regulationLocation", regulationContext.location());
            }
            if (regulationContext.interactingAgents() != null && !regulationContext.interactingAgents().isEmpty()) {
                props.put("regulationAgents", String.join(",", regulationContext.interactingAgents()));
            }
            if (regulationContext.topic() != null) {
                props.put("regulationTopic", regulationContext.topic());
            }
        }

        store.addNode(
                NodeInput.of("emotion-regulation", cognitiveSubgraphId)
                         .withProvenance("emotion-regulation")
                         .withProperties(props),
                tenantId);
    }


    public static List<EmotionRegulationResult> loadHistory(MindMapStore store,
                                                             String agentId, String tenantId,
                                                             int limit) {
        return store.search(MindMapQuery.of(tenantId, 200).withType(SubgraphTypes.COGNITIVE))
                .stream()
                .filter(n -> COGNITIVE_KIND.equals(n.properties().get("cognitiveKind")))
                .filter(n -> agentId.equals(n.properties().get("agent-id")))
                .sorted(Comparator.comparing(
                        (MindMapNode n) -> n.properties().getOrDefault("timestamp", ""))
                        .reversed())
                .limit(limit)
                .map(EmotionRegulationMindMapAdapter::fromNode)
                .toList();
    }

    public record SourceOutcomeSummary(String source, int episodeCount, double meanEffectiveness) {}

    public static java.util.Map<String, SourceOutcomeSummary> loadOutcomeSummaryBySource(
            MindMapStore store, String agentId, String tenantId, int episodeLimit) {
        var nodes = store.search(MindMapQuery.of(tenantId, episodeLimit).withType(SubgraphTypes.COGNITIVE))
                         .stream()
                         .filter(n -> COGNITIVE_KIND.equals(n.properties().get("cognitiveKind")))
                         .filter(n -> agentId.equals(n.properties().get("agent-id")))
                         .toList();

        var bySource = new HashMap<String, java.util.List<Double>>();
        for (var node : nodes) {
            String source        = node.properties().getOrDefault("strategySource", "UNKNOWN");
            double effectiveness = parseDouble(node.properties().get("effectiveness"));
            bySource.computeIfAbsent(source, k -> new java.util.ArrayList<>()).add(effectiveness);
        }

        var result = new HashMap<String, SourceOutcomeSummary>();
        for (var entry : bySource.entrySet()) {
            var    scores = entry.getValue();
            double mean   = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            result.put(entry.getKey(), new SourceOutcomeSummary(entry.getKey(), scores.size(), mean));
        }
        return result;
    }


    public static List<RegulationOutcome> loadOutcomes(MindMapStore store,
                                                       String agentId, String tenantId,
                                                       io.casehub.neocortex.cognitive.EmotionType emotionType,
                                                       int limit) {
        String emotionName = emotionType.name();
        return store.search(MindMapQuery.of(tenantId, 200).withType(SubgraphTypes.COGNITIVE))
                    .stream()
                    .filter(n -> COGNITIVE_KIND.equals(n.properties().get("cognitiveKind")))
                    .filter(n -> agentId.equals(n.properties().get("agent-id")))
                    .filter(n -> emotionName.equals(n.properties().get("dominantEmotionType")))
                    .sorted(Comparator.comparing(
                                              (MindMapNode n) -> n.properties().getOrDefault("timestamp", ""))
                                      .reversed())
                    .limit(limit)
                    .map(n -> {
                        var result = fromNode(n);
                        var ts = n.properties().get("timestamp");
                        var instant = ts != null ? java.time.Instant.parse(ts) : java.time.Instant.now();
                        return RegulationOutcome.of(result, instant);
                    })
                    .toList();
    }

    public static List<EmotionRegulationResult> loadHistory(MindMapStore store,
                                                            String agentId, String tenantId,
                                                            io.casehub.neocortex.cognitive.EmotionType emotionType,
                                                            int limit) {
        String emotionName = emotionType.name();
        return store.search(MindMapQuery.of(tenantId, 200).withType(SubgraphTypes.COGNITIVE))
                    .stream()
                    .filter(n -> COGNITIVE_KIND.equals(n.properties().get("cognitiveKind")))
                    .filter(n -> agentId.equals(n.properties().get("agent-id")))
                    .filter(n -> emotionName.equals(n.properties().get("dominantEmotionType")))
                    .sorted(Comparator.comparing(
                                              (MindMapNode n) -> n.properties().getOrDefault("timestamp", ""))
                                      .reversed())
                    .limit(limit)
                    .map(EmotionRegulationMindMapAdapter::fromNode)
                    .toList();
    }


    private static EmotionRegulationResult fromNode(MindMapNode node) {
        var props = node.properties();
        var strategy = EmotionRegulationStrategy.valueOf(props.get("strategy"));
        var targetEmotions = splitList(props.getOrDefault("targetEmotions", ""));
        var targetDrives = splitList(props.getOrDefault("targetDrives", ""));
        double peakOriginalIntensity = parseDouble(props.get("peakOriginalIntensity"));
        double regulatedIntensity = parseDouble(props.get("regulatedIntensity"));
        double effectiveness = parseDouble(props.get("effectiveness"));
        String narrative = props.getOrDefault("narrative", "");

        DriveAxis regulationAxis = null;
        String axisName = props.get("regulationAxis");
        if (axisName != null) {
            try { regulationAxis = DriveAxis.valueOf(axisName); }
            catch (IllegalArgumentException ignored) {}
        }

        String environmentalDirective = props.get("environmentalDirective");

        return new EmotionRegulationResult(
                strategy, targetEmotions, targetDrives,
                peakOriginalIntensity, regulatedIntensity, effectiveness,
                narrative, regulationAxis, environmentalDirective);
    }

    private static List<String> splitList(String csv) {
        if (csv.isEmpty()) return List.of();
        return List.of(csv.split(","));
    }

    private static double parseDouble(String value) {
        if (value == null) return 0.0;
        try { return Double.parseDouble(value); }
        catch (NumberFormatException e) { return 0.0; }
    }
}
