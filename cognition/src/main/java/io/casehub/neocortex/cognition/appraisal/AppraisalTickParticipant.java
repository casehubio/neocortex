package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.core.CognitionTickParticipant;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodSignal;
import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AppraisalTickParticipant implements CognitionTickParticipant {

    private final SalienceStrategy salienceStrategy;
    private final AppraisalStrategy appraisalStrategy;
    private final DriveOrchestrator driveOrchestrator;
    private final MoodOrchestrator moodOrchestrator;
    private final @Nullable CognitiveDefaultsRegistry defaultsRegistry;
    private final CognitionConfig config;
    private final @Nullable MindMapStore mindMapStore;


    private static final String DRIVE_INTENSITY_KIND = "drive-intensity";
    private final AppraisalThresholds thresholds;

    private final ConcurrentHashMap<String, AppraisalResult> results = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HabituationState> habituationStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Double>> concernSaliences = new ConcurrentHashMap<>();

    public Map<String, Double> currentConcernSalience(String agentId, String tenantId) {
        return concernSaliences.getOrDefault(agentId + ":" + tenantId, Map.of());
    }

    public AppraisalThresholds thresholds() {
        return thresholds;
    }


    public AppraisalTickParticipant(
            SalienceStrategy salienceStrategy,
            AppraisalStrategy appraisalStrategy,
            DriveOrchestrator driveOrchestrator,
            MoodOrchestrator moodOrchestrator,
            @Nullable CognitiveDefaultsRegistry defaultsRegistry,
            CognitionConfig config) {
        this(salienceStrategy, appraisalStrategy, driveOrchestrator,
             moodOrchestrator, defaultsRegistry, config, null, AppraisalThresholds.defaults());
    }

    public AppraisalTickParticipant(
            SalienceStrategy salienceStrategy,
            AppraisalStrategy appraisalStrategy,
            DriveOrchestrator driveOrchestrator,
            MoodOrchestrator moodOrchestrator,
            @Nullable CognitiveDefaultsRegistry defaultsRegistry,
            CognitionConfig config,
            @Nullable MindMapStore mindMapStore) {
        this(salienceStrategy, appraisalStrategy, driveOrchestrator,
             moodOrchestrator, defaultsRegistry, config, mindMapStore, AppraisalThresholds.defaults());
    }

    public AppraisalTickParticipant(
            SalienceStrategy salienceStrategy,
            AppraisalStrategy appraisalStrategy,
            DriveOrchestrator driveOrchestrator,
            MoodOrchestrator moodOrchestrator,
            @Nullable CognitiveDefaultsRegistry defaultsRegistry,
            CognitionConfig config,
            @Nullable MindMapStore mindMapStore,
            AppraisalThresholds thresholds) {
        this.salienceStrategy  = salienceStrategy;
        this.appraisalStrategy = appraisalStrategy;
        this.driveOrchestrator = driveOrchestrator;
        this.moodOrchestrator  = moodOrchestrator;
        this.defaultsRegistry  = defaultsRegistry;
        this.config            = config;
        this.mindMapStore      = mindMapStore;
        this.thresholds        = thresholds;
    }


    @Override
    public void tick(CognitionTickContext context) {
        if (!config.appraisalEnabled()) {return;}

        var agentKey    = context.agentId() + ":" + context.tenantId();
        var observation = context.observation();

        var profileOpt = driveOrchestrator.currentDrives(context.agentId(), context.tenantId());
        if (profileOpt.isEmpty()) {return;}
        var drives = new ArrayList<>(profileOpt.get().allDrives());

        List<MindMapNode> driveNodes = List.of();
        if (mindMapStore != null) {
            driveNodes = loadDriveIntensityNodes(context.agentId(), context.tenantId());
            for (var node : driveNodes) {
                String driveType = node.properties().getOrDefault("drive-type", node.name());
                double intensity = parseDoubleSafe(node.properties().get("intensity"), 0.5);
                drives.add(new Drive(driveType, DriveCategory.CHARACTER, intensity, "adapted"));
            }
        }

        var mood = moodOrchestrator.currentMood(context.agentId(), context.tenantId())
                                   .orElse(null);

        var defaults = defaultsRegistry != null
                       ? defaultsRegistry.forAgentOrDefaults(context.agentId()) : null;
        var weights   = defaults != null ? defaults.appraisalWeights() : null;
        var habConfig = defaults != null ? defaults.habituationConfig() : null;
        var disposition = defaults != null && defaults.descriptor() != null
                          ? defaults.descriptor().disposition() : null;
        var habituation = habituationStates.getOrDefault(agentKey, HabituationState.empty());

        PerceivedSituation situation;
        if (config.salienceEnabled() && observation != null) {
            situation = salienceStrategy.perceive(
                    new SalienceContext(observation, drives, mood, List.of(), List.of()));
        } else if (observation != null) {
            situation = PerceivedSituation.passThrough(observation);
        } else {
            return;
        }

        var result = appraisalStrategy.appraise(
                new AppraisalContext(situation, drives, weights, habConfig, habituation, mood, disposition));

        results.put(agentKey, result);
        habituationStates.put(agentKey, result.updatedHabituation());

        if (mindMapStore != null && observation != null) {
            var salience = computeConcernSalience(observation, driveNodes,
                                                  context.agentId(), context.tenantId());
            concernSaliences.put(agentKey, salience);
        }

        bridgeToMood(result, context);
    }

    public Optional<AppraisalResult> currentResult(String agentId, String tenantId) {
        return Optional.ofNullable(results.get(agentId + ":" + tenantId));
    }

    private List<MindMapNode> loadDriveIntensityNodes(String agentId, String tenantId) {
        return mindMapStore.search(MindMapQuery.of(tenantId, 200).withType(SubgraphTypes.COGNITIVE))
                           .stream()
                           .filter(n -> DRIVE_INTENSITY_KIND.equals(n.properties().get("cognitiveKind")))
                           .filter(n -> agentId.equals(n.properties().get("agent-id")))
                           .toList();
    }

    private Map<String, Double> computeConcernSalience(
            String observation, List<MindMapNode> driveNodes,
            String agentId, String tenantId) {
        var salience = new HashMap<String, Double>();
        int minLen = thresholds.tokenMinLength();
        var obsWords = tokenize(observation, minLen);
        if (obsWords.isEmpty()) return salience;

        for (var node : driveNodes) {
            String desc = node.properties().get("description");
            if (desc == null || desc.isBlank()) continue;
            String driveType = node.properties().getOrDefault("drive-type", node.name());
            double score = keywordOverlap(obsWords, tokenize(desc, minLen));
            if (score > thresholds.concernSalienceThreshold()) salience.put(driveType, score);
        }

        var goalNodes = mindMapStore.search(MindMapQuery.of(tenantId, 200).withType(SubgraphTypes.GOAL));
        for (var node : goalNodes) {
            if (!"active".equals(node.properties().get("status"))) continue;
            if (!agentId.equals(node.properties().get("agent-id"))) continue;
            double score = keywordOverlap(obsWords, tokenize(node.name(), minLen));
            if (score > thresholds.concernSalienceThreshold()) salience.put(node.name(), score);
        }

        return Map.copyOf(salience);
    }

    private static double keywordOverlap(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        long shared = a.stream().filter(b::contains).count();
        return (double) shared / Math.max(a.size(), b.size());
    }

    private static Set<String> tokenize(String text, int minLength) {
        return Arrays.stream(text.toLowerCase().split("\\W+"))
                     .filter(w -> w.length() > minLength)
                     .collect(Collectors.toSet());
    }

    private static double parseDoubleSafe(String value, double fallback) {
        if (value == null) return fallback;
        try { return Double.parseDouble(value); }
        catch (NumberFormatException e) { return fallback; }
    }


    private void bridgeToMood(AppraisalResult result, CognitionTickContext context) {
        if (result.emotions().isEmpty()) return;

        double totalIntensity = result.emotions().stream()
                .mapToDouble(CognitiveEmotion::intensity).sum();
        if (totalIntensity <= 0) return;

        double p = result.emotions().stream()
                .mapToDouble(e -> e.intensity() * e.pad().pleasure()).sum() / totalIntensity;
        double a = result.emotions().stream()
                .mapToDouble(e -> e.intensity() * e.pad().arousal()).sum() / totalIntensity;
        double d = result.emotions().stream()
                .mapToDouble(e -> e.intensity() * e.pad().dominance()).sum() / totalIntensity;

        moodOrchestrator.record(
                new MoodSignal.DirectShift(clamp(p), clamp(a), clamp(d), "appraisal-emotions"),
                context.agentId(), context.tenantId());
    }

    private static double clamp(double value) {
        return Math.max(-2.0, Math.min(2.0, value));
    }
}
