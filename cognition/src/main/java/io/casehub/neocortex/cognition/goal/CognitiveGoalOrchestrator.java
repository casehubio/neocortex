package io.casehub.neocortex.cognition.goal;

import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.core.CognitionTickParticipant;
import io.casehub.neocortex.cognitive.PadProjection;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.mindmap.AppraisalContext;
import io.casehub.neocortex.mindmap.GoalAppraisal;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.SubgraphTypes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;

public class CognitiveGoalOrchestrator implements CognitionTickParticipant {

    private static final System.Logger LOG = System.getLogger(CognitiveGoalOrchestrator.class.getName());
    private static final Set<String> SURFACEABLE_STATUSES = Set.of("active", "blocked");

    private final MindMapStore mindMapStore;
    private final GoalAppraisal appraisal;
    private final CaseMemoryStore memoryStore;
    private final CognitiveGoalConfig config;
    private final BiFunction<String, String, PadProjection> moodBaselineProvider;
    private final Clock clock;

    private final ConcurrentHashMap<String, CognitiveGoalState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastSurfacedTimestamps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> tickLocks = new ConcurrentHashMap<>();

    public CognitiveGoalOrchestrator(
            MindMapStore mindMapStore,
            GoalAppraisal appraisal,
            CaseMemoryStore memoryStore,
            CognitiveGoalConfig config,
            BiFunction<String, String, PadProjection> moodBaselineProvider,
            Clock clock) {
        this.mindMapStore = mindMapStore;
        this.appraisal = appraisal;
        this.memoryStore = memoryStore;
        this.config = config;
        this.moodBaselineProvider = moodBaselineProvider;
        this.clock = clock;
    }

    @Override
    public void tick(CognitionTickContext context) {
        String agentId = context.agentId();
        String tenantId = context.tenantId();
        String key = agentId + "|" + tenantId;

        withLock(key, () -> doTick(agentId, tenantId, key));
    }

    public Optional<CognitiveGoalState> currentState(String agentId, String tenantId) {
        return Optional.ofNullable(states.get(agentId + "|" + tenantId));
    }

    public List<GoalRevision> pendingRevisions(String agentId, String tenantId) {
        var state = states.get(agentId + "|" + tenantId);
        return state != null ? state.revisions() : List.of();
    }

    private void doTick(String agentId, String tenantId, String key) {
        List<MindMapNode> goals = queryGoals(tenantId, agentId);
        if (goals.isEmpty()) {
            states.put(key, new CognitiveGoalState(List.of(), List.of()));
            return;
        }

        Instant now = clock.instant();
        PadProjection baseline = moodBaselineProvider.apply(agentId, tenantId);

        List<MindMapNode> selected = selectForSurfacing(goals, agentId, now);

        var emotions = new ArrayList<CognitiveGoalState.GoalEmotion>();
        var revisions = new ArrayList<GoalRevision>();

        for (var goal : selected) {
            int surfacingGap = intProperty(goal, "surfacing-progress-gap",
                    intProperty(goal, "surfaced-count", 0));
            String lastProgressStr = goal.property("last-progress-at").orElse(null);
            Instant lastProgress = lastProgressStr != null ? parseInstant(lastProgressStr) : null;
            String lastSurfacedStr = goal.property("last-surfaced-at").orElse(null);
            Instant lastSurfaced = lastSurfacedStr != null ? parseInstant(lastSurfacedStr) : null;

            var ctx = new AppraisalContext(tenantId, agentId, baseline,
                    surfacingGap, lastProgress, lastSurfaced, Map.of(), null);
            var goalEmotions = appraisal.appraise(goal, ctx);
            emotions.add(new CognitiveGoalState.GoalEmotion(goal, goalEmotions));

            lastSurfacedTimestamps.put(agentId + "|" + goal.id(), now);
        }

        for (var goal : goals) {
            checkDecaySignal(goal, revisions);
        }

        states.put(key, new CognitiveGoalState(List.copyOf(emotions), List.copyOf(revisions)));
    }

    private List<MindMapNode> queryGoals(String tenantId, String agentId) {
        for (MindMapSubgraph sg : mindMapStore.listSubgraphs(tenantId)) {
            if (SubgraphTypes.GOAL.equals(sg.type())) {
                return mindMapStore.nodesIn(sg.id(), tenantId).stream()
                                   .filter(node -> {
                                       var nodeAgentId = node.property("agent-id").orElse(null);
                                       return nodeAgentId == null || agentId.equals(nodeAgentId);
                                   })
                                   .toList();
            }
        }
        return List.of();
    }

    private List<MindMapNode> selectForSurfacing(List<MindMapNode> goals, String agentId, Instant now) {
        var selected = new ArrayList<MindMapNode>();
        for (var goal : goals) {
            String status = goal.property("status").orElse("active");
            if (!SURFACEABLE_STATUSES.contains(status)) continue;

            String cooldownKey = agentId + "|" + goal.id();
            Instant lastSurfaced = lastSurfacedTimestamps.get(cooldownKey);
            if (lastSurfaced != null && Duration.between(lastSurfaced, now).compareTo(config.surfacingCooldown()) < 0) {
                continue;
            }

            Optional<String> priorityStr = goal.property("priority");
            if (priorityStr.isPresent()) {
                double priority = parseDouble(priorityStr.get(), 0);
                if (priority < config.minimumSurfacingPriority()) continue;
            }

            selected.add(goal);
        }
        return selected;
    }

    private void checkDecaySignal(MindMapNode goal, List<GoalRevision> revisions) {
        goal.property("decay-signal").ifPresent(signal -> {
            String eidosGoalName = goal.property("eidos-goal-name").orElse(null);
            revisions.add(new GoalRevision(goal.id(), goal.name(), signal, eidosGoalName));
        });
    }

    private static int intProperty(MindMapNode node, String key, int defaultValue) {
        return node.property(key)
                .map(v -> { try { return Integer.parseInt(v); } catch (NumberFormatException e) { return defaultValue; } })
                .orElse(defaultValue);
    }

    private static double parseDouble(String value, double defaultValue) {
        try { return Double.parseDouble(value); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    private static Instant parseInstant(String value) {
        try { return Instant.parse(value); }
        catch (Exception e) { return null; }
    }

    private void withLock(String key, Runnable action) {
        var lock = tickLocks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }
}
