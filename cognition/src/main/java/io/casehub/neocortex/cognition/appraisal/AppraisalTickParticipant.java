package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.core.CognitionTickParticipant;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodSignal;
import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.mindmap.MindMapStore;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AppraisalTickParticipant implements CognitionTickParticipant {

    private final SalienceStrategy salienceStrategy;
    private final AppraisalStrategy appraisalStrategy;
    private final DriveOrchestrator driveOrchestrator;
    private final MoodOrchestrator moodOrchestrator;
    private final @Nullable CognitiveDefaultsRegistry defaultsRegistry;
    private final CognitionConfig config;
    private final @Nullable MindMapStore mindMapStore;


    private final ConcurrentHashMap<String, AppraisalResult> results = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HabituationState> habituationStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, java.util.Map<String, Double>> concernSaliences = new ConcurrentHashMap<>();

    public java.util.Map<String, Double> currentConcernSalience(String agentId, String tenantId) {
        return concernSaliences.getOrDefault(agentId + ":" + tenantId, java.util.Map.of());
    }


    public AppraisalTickParticipant(
            SalienceStrategy salienceStrategy,
            AppraisalStrategy appraisalStrategy,
            DriveOrchestrator driveOrchestrator,
            MoodOrchestrator moodOrchestrator,
            @Nullable CognitiveDefaultsRegistry defaultsRegistry,
            CognitionConfig config) {
        this(salienceStrategy, appraisalStrategy, driveOrchestrator,
             moodOrchestrator, defaultsRegistry, config, null);
    }

    public AppraisalTickParticipant(
            SalienceStrategy salienceStrategy,
            AppraisalStrategy appraisalStrategy,
            DriveOrchestrator driveOrchestrator,
            MoodOrchestrator moodOrchestrator,
            @Nullable CognitiveDefaultsRegistry defaultsRegistry,
            CognitionConfig config,
            @Nullable MindMapStore mindMapStore) {
        this.salienceStrategy  = salienceStrategy;
        this.appraisalStrategy = appraisalStrategy;
        this.driveOrchestrator = driveOrchestrator;
        this.moodOrchestrator  = moodOrchestrator;
        this.defaultsRegistry  = defaultsRegistry;
        this.config            = config;
        this.mindMapStore      = mindMapStore;
    }


    @Override
    public void tick(CognitionTickContext context) {
        if (!config.appraisalEnabled()) {return;}

        var agentKey    = context.agentId() + ":" + context.tenantId();
        var observation = context.observation();

        var profileOpt = driveOrchestrator.currentDrives(context.agentId(), context.tenantId());
        if (profileOpt.isEmpty()) {return;}
        var drives = new java.util.ArrayList<>(profileOpt.get().allDrives());

        if (mindMapStore != null) {
            var characterDrives = loadCharacterDrives(context.agentId(), context.tenantId());
            drives.addAll(characterDrives);
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
            var salience = computeConcernSalience(observation, context.agentId(), context.tenantId());
            concernSaliences.put(agentKey, salience);
        }

        bridgeToMood(result, context);
    }

    public Optional<AppraisalResult> currentResult(String agentId, String tenantId) {
        return Optional.ofNullable(results.get(agentId + ":" + tenantId));
    }

    private List<Drive> loadCharacterDrives(String agentId, String tenantId) {
        var query = io.casehub.neocortex.mindmap.MindMapQuery.of(tenantId, 200)
                                                             .withType(io.casehub.neocortex.mindmap.SubgraphTypes.COGNITIVE);
        return mindMapStore.search(query).stream()
                           .filter(n -> "drive-intensity".equals(n.properties().get("cognitiveKind")))
                           .filter(n -> agentId.equals(n.properties().get("agent-id")))
                           .map(n -> {
                               String driveType = n.properties().getOrDefault("drive-type", n.name());
                               double intensity = Double.parseDouble(
                                       n.properties().getOrDefault("intensity", "0.5"));
                               return new Drive(driveType, DriveCategory.CHARACTER, intensity, "adapted");
                           })
                           .toList();
    }

    private java.util.Map<String, Double> computeConcernSalience(
            String observation, String agentId, String tenantId) {
        var salience = new java.util.HashMap<String, Double>();
        var obsWords = tokenize(observation);
        if (obsWords.isEmpty()) {return salience;}

        var driveNodes = mindMapStore.search(
                io.casehub.neocortex.mindmap.MindMapQuery.of(tenantId, 200)
                                                         .withType(io.casehub.neocortex.mindmap.SubgraphTypes.COGNITIVE));
        for (var node : driveNodes) {
            if (!"drive-intensity".equals(node.properties().get("cognitiveKind"))) {continue;}
            if (!agentId.equals(node.properties().get("agent-id"))) {continue;}
            String desc = node.properties().get("description");
            if (desc == null || desc.isBlank()) {continue;}
            String driveType = node.properties().getOrDefault("drive-type", node.name());
            double score     = keywordOverlap(obsWords, tokenize(desc));
            if (score > 0) {salience.put(driveType, score);}
        }

        var goalNodes = mindMapStore.search(
                io.casehub.neocortex.mindmap.MindMapQuery.of(tenantId, 200)
                                                         .withType(io.casehub.neocortex.mindmap.SubgraphTypes.GOAL));
        for (var node : goalNodes) {
            if (!"active".equals(node.properties().get("status"))) {continue;}
            if (!agentId.equals(node.properties().get("agent-id"))) {continue;}
            double score = keywordOverlap(obsWords, tokenize(node.name()));
            if (score > 0) {salience.put(node.name(), score);}
        }

        return java.util.Map.copyOf(salience);
    }

    private static double keywordOverlap(java.util.Set<String> a, java.util.Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {return 0.0;}
        long shared = a.stream().filter(b::contains).count();
        return (double) shared / Math.max(a.size(), b.size());
    }

    private static java.util.Set<String> tokenize(String text) {
        return java.util.Arrays.stream(text.toLowerCase().split("\\W+"))
                               .filter(w -> w.length() > 2)
                               .collect(java.util.stream.Collectors.toSet());
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
