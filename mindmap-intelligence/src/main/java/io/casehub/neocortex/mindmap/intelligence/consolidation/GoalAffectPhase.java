package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.PadProjection;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.memory.experience.Observation;
import io.casehub.neocortex.mindmap.AppraisalContext;
import io.casehub.neocortex.mindmap.AppraisalWeights;
import io.casehub.neocortex.mindmap.AttentionSignal;
import io.casehub.neocortex.mindmap.GoalAppraisal;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SignalCategory;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@ApplicationScoped
@Priority(37)
public class GoalAffectPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(GoalAffectPhase.class.getName());

    private final MindMapStore          store;
    private final Clock                 clock;
    private final GoalAppraisal         appraisal;
    private final GoalLifecycleConfig   config;
    private final List<AttentionSignal> pendingSignals = new ArrayList<>();
    private final CaseMemoryStore       memoryStore;


    @Inject
    public GoalAffectPhase(Instance<MindMapStore> store, Instance<GoalAppraisal> appraisal,
                           Instance<CaseMemoryStore> memoryStore, Instance<GoalLifecycleConfig> config) {
        this.store       = store.isResolvable() ? store.get() : null;
        this.appraisal   = appraisal.isResolvable() ? appraisal.get() : null;
        this.memoryStore = memoryStore.isResolvable() ? memoryStore.get() : null;
        this.config      = config.isResolvable() ? config.get() : null;
        this.clock       = Clock.systemUTC();
    }

    public GoalAffectPhase(MindMapStore store, GoalAppraisal appraisal,
                           CaseMemoryStore memoryStore, Clock clock, GoalLifecycleConfig config) {
        this.store       = store;
        this.appraisal   = appraisal;
        this.memoryStore = memoryStore;
        this.config      = config;
        this.clock       = clock;
    }

    public GoalAffectPhase(MindMapStore store, GoalAppraisal appraisal, CaseMemoryStore memoryStore, Clock clock) {
        this(store, appraisal, memoryStore, clock, null);
    }

    public GoalAffectPhase(MindMapStore store, Clock clock) {
        this(store, null, null, clock, null);
    }

    public GoalAffectPhase(MindMapStore store) {
        this(store, null, null, Clock.systemUTC(), null);
    }

    private double legacyAffectDeltaThreshold() {return config != null ? config.legacyAffectDeltaThreshold() : 0.3;}

    @Override
    public String name() {
        return "goal-affect";
    }

    @Override
    public void beginTick() {
        pendingSignals.clear();
    }

    @Override
    public List<AttentionSignal> signals() {
        var result = List.copyOf(pendingSignals);
        pendingSignals.clear();
        return result;
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (store == null) {return;}

        String goalSgId = findGoalSubgraph(tenantId);
        if (goalSgId == null) {return;}

        Instant now = clock.instant();

        for (MindMapNode node : store.nodesIn(goalSgId, tenantId)) {
            if (appraisal != null) {
                applyOccAppraisal(node, tenantId, now);
            } else {
                applyLegacyPad(node, tenantId, now);
            }
        }
    }

    private void applyOccAppraisal(MindMapNode node, String tenantId, Instant now) {
        int     surfacingCount  = intProperty(node, "surfacing-progress-gap", intProperty(node, "surfaced-count", 0));
        String  lastProgressStr = node.property("last-progress-at").orElse(null);
        Instant lastProgress    = lastProgressStr != null ? parseInstant(lastProgressStr) : null;
        String  lastSurfacedStr = node.property("last-surfaced-at").orElse(null);
        Instant lastSurfaced    = lastSurfacedStr != null ? parseInstant(lastSurfacedStr) : null;

        var ctx = new AppraisalContext(tenantId, "consolidation",
                                       PadProjection.NEUTRAL, surfacingCount, lastProgress, lastSurfaced, Map.of(),
                                       AppraisalWeights.NEUTRAL);

        var emotions = appraisal.appraise(node, ctx);
        if (emotions.isEmpty()) {return;}

        var dominant = emotions.stream()
                               .max(java.util.Comparator.comparingDouble(CognitiveEmotion::intensity))
                               .orElse(null);
        if (dominant == null) {return;}

        var nodeUpdate = NodeUpdate.empty().withPad(
                dominant.pad().pleasure(),
                dominant.pad().arousal(),
                dominant.pad().dominance());

        String  status     = node.property("status").orElse("active");
        boolean isTerminal = "completed".equals(status) || "abandoned".equals(status) || "dormant".equals(status);
        if (isTerminal && node.property("outcome-emotion").isEmpty()) {
            var outcomeProps = new HashMap<String, String>();
            outcomeProps.put("outcome-emotion", dominant.type().name());
            outcomeProps.put("outcome-emotion-intensity", String.format("%.2f", dominant.intensity()));
            node.property("initial-emotion-intensity").ifPresent(initialStr -> {
                try {
                    double initial = Double.parseDouble(initialStr);
                    outcomeProps.put("emotion-gap", String.format("%.2f", dominant.intensity() - initial));
                } catch (NumberFormatException ignored) {}
            });
            nodeUpdate = nodeUpdate.withPropertiesToSet(outcomeProps);
        }

        store.updateNode(node.id(), nodeUpdate, tenantId);

        String agentId = node.property("agent-id").orElse(null);
        if (memoryStore != null && agentId != null) {
            var metadata = new HashMap<String, String>();
            metadata.put("emotion-type", dominant.type().name());
            metadata.put("emotion-intensity", String.valueOf(dominant.intensity()));
            var observation = new Observation(
                    agentId, tenantId, null, null, now,
                    "OCC emotion: " + dominant.type().name(),
                    dominant.intensity(), metadata, node.name());
            memoryStore.store(ExperienceEvents.toMemoryInput(observation));
        }

        pendingSignals.add(new AttentionSignal(
                agentId, tenantId, SignalCategory.AFFECT_CHANGE,
                node.id(), node.name(), dominant.intensity(),
                dominant.type() + " (intensity " + String.format("%.2f", dominant.intensity()) + ")"));
    }

    private void applyLegacyPad(MindMapNode node, String tenantId, Instant now) {
        String status  = node.property("status").orElse("active");
        double urgency = GoalUrgency.computeUrgency(node, now);

        Double pleasure  = null;
        Double arousal   = null;
        Double dominance = null;

        switch (status) {
            case "active" -> {
                arousal   = urgency * d(config, GoalLifecycleConfig::activeUrgencyArousalFactor, 0.8);
                pleasure  = d(config, GoalLifecycleConfig::activePleasure, 0.2);
                dominance = d(config, GoalLifecycleConfig::activeDominance, 0.3);
            }
            case "blocked" -> {
                pleasure  = d(config, GoalLifecycleConfig::blockedPleasure, -0.4) - (urgency * d(config, GoalLifecycleConfig::blockedUrgencyPleasureFactor, 0.4));
                arousal   = d(config, GoalLifecycleConfig::blockedArousal, 0.3) + (urgency * d(config, GoalLifecycleConfig::blockedUrgencyArousalFactor, 0.5));
                dominance = d(config, GoalLifecycleConfig::blockedDominance, -0.3);
            }
            case "completed" -> {
                pleasure  = d(config, GoalLifecycleConfig::completedPleasure, 0.6);
                arousal   = d(config, GoalLifecycleConfig::completedArousal, 0.2);
                dominance = d(config, GoalLifecycleConfig::completedDominance, 0.5);
            }
            case "dormant" -> {
                pleasure  = d(config, GoalLifecycleConfig::dormantPleasure, 0.0);
                arousal   = d(config, GoalLifecycleConfig::dormantArousal, -0.3);
                dominance = d(config, GoalLifecycleConfig::dormantDominance, 0.0);
            }
            case "abandoned" -> {
                pleasure  = d(config, GoalLifecycleConfig::abandonedPleasure, -0.2);
                arousal   = d(config, GoalLifecycleConfig::abandonedArousal, -0.2);
                dominance = d(config, GoalLifecycleConfig::abandonedDominance, -0.1);
            }
            default -> {}
        }

        if (pleasure != null) {
            Double oldPleasure = node.pleasure();
            double delta = oldPleasure != null
                           ? Math.abs(pleasure - oldPleasure)
                           : Math.abs(pleasure);
            store.updateNode(node.id(),
                             NodeUpdate.empty().withPad(pleasure, arousal, dominance),
                             tenantId);
            if (delta > legacyAffectDeltaThreshold()) {
                pendingSignals.add(new AttentionSignal(
                        node.property("agent-id").orElse(null), tenantId,
                        SignalCategory.AFFECT_CHANGE, node.id(), node.name(), delta,
                        status + " — pleasure delta " + String.format("%.2f", delta)));
            }
        }
    }

    private String findGoalSubgraph(String tenantId) {
        for (MindMapSubgraph sg : store.listSubgraphs(tenantId)) {
            if (SubgraphTypes.GOAL.equals(sg.type())) {
                return sg.id();
            }
        }
        return null;
    }

    private static int intProperty(MindMapNode node, String key, int defaultValue) {
        return node.property(key)
                   .map(v -> {try {return Integer.parseInt(v);} catch (NumberFormatException e) {return defaultValue;}})
                   .orElse(defaultValue);
    }


    private static double d(GoalLifecycleConfig config, java.util.function.ToDoubleFunction<GoalLifecycleConfig> fn, double fallback) {
        return config != null ? fn.applyAsDouble(config) : fallback;
    }

    private static Instant parseInstant(String value) {
        try {return Instant.parse(value);} catch (Exception e) {return null;}
    }
}
