package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryScanRequest;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@ApplicationScoped
@Priority(12)
public class SurfacingAggregationPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(SurfacingAggregationPhase.class.getName());
    private static final int SCAN_PAGE_SIZE = 200;

    private final MindMapStore mindMapStore;
    private final CaseMemoryStore memoryStore;

    @Inject
    public SurfacingAggregationPhase(Instance<MindMapStore> mindMapStore,
                                      Instance<CaseMemoryStore> memoryStore) {
        this.mindMapStore = mindMapStore.isResolvable() ? mindMapStore.get() : null;
        this.memoryStore = memoryStore.isResolvable() ? memoryStore.get() : null;
    }

    public SurfacingAggregationPhase(MindMapStore mindMapStore, CaseMemoryStore memoryStore) {
        this.mindMapStore = mindMapStore;
        this.memoryStore = memoryStore;
    }

    @Override
    public String name() {
        return "surfacing-aggregation";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (mindMapStore == null || memoryStore == null) return;

        String goalSgId = findGoalSubgraph(tenantId);
        if (goalSgId == null) return;

        List<Memory> surfacingEvents = scanSurfacingEvents(tenantId);
        List<Memory> progressEvents = scanProgressEvents(tenantId);

        Map<String, List<Memory>> surfacingsByGoal = groupByGoalNodeId(surfacingEvents);
        Map<String, List<Memory>> progressByGoal = groupByGoalNodeId(progressEvents);

        for (var entry : surfacingsByGoal.entrySet()) {
            String goalNodeId = entry.getKey();
            List<Memory> surfacings = entry.getValue();

            try {
                var existing = mindMapStore.getNode(goalNodeId, tenantId);
                if (existing == null) continue;
            } catch (Exception e) {
                continue;
            }

            int surfacedCount = surfacings.size();
            Instant firstSurfaced = surfacings.stream()
                    .map(Memory::createdAt)
                    .min(Instant::compareTo)
                    .orElse(null);
            Instant lastSurfaced = surfacings.stream()
                    .map(Memory::createdAt)
                    .max(Instant::compareTo)
                    .orElse(null);

            List<Memory> progress = progressByGoal.getOrDefault(goalNodeId, List.of());
            Instant lastProgress = progress.stream()
                    .map(Memory::createdAt)
                    .max(Instant::compareTo)
                    .orElse(null);

            int gap;
            if (lastProgress == null) {
                gap = surfacedCount;
            } else {
                gap = (int) surfacings.stream()
                        .filter(s -> s.createdAt().isAfter(lastProgress))
                        .count();
            }

            var props = new HashMap<String, String>();
            props.put("surfaced-count", String.valueOf(surfacedCount));
            if (firstSurfaced != null) props.put("first-surfaced-at", firstSurfaced.toString());
            if (lastSurfaced != null) props.put("last-surfaced-at", lastSurfaced.toString());
            if (lastProgress != null) props.put("last-progress-at", lastProgress.toString());
            props.put("surfacing-progress-gap", String.valueOf(gap));

            mindMapStore.updateNode(goalNodeId,
                    NodeUpdate.empty().withPropertiesToSet(props), tenantId);
        }
    }

    private List<Memory> scanSurfacingEvents(String tenantId) {
        return scanByAttribute(tenantId, "cognitive-event", "goal-surfaced");
    }

    private List<Memory> scanProgressEvents(String tenantId) {
        return scanByAttribute(tenantId, "cognitive-event", "goal-progress");
    }

    private List<Memory> scanByAttribute(String tenantId, String key, String value) {
        var results = new ArrayList<Memory>();
        String afterId = null;
        while (true) {
            var request = new MemoryScanRequest(tenantId, "experience", key, value,
                    SCAN_PAGE_SIZE, afterId);
            var page = memoryStore.scan(request);
            if (page.isEmpty()) break;
            results.addAll(page);
            afterId = page.get(page.size() - 1).memoryId();
            if (page.size() < SCAN_PAGE_SIZE) break;
        }
        return results;
    }

    private Map<String, List<Memory>> groupByGoalNodeId(List<Memory> memories) {
        var result = new HashMap<String, List<Memory>>();
        for (var memory : memories) {
            String goalNodeId = memory.attributes().get("goal-node-id");
            if (goalNodeId != null) {
                result.computeIfAbsent(goalNodeId, k -> new ArrayList<>()).add(memory);
            }
        }
        return result;
    }

    private String findGoalSubgraph(String tenantId) {
        for (MindMapSubgraph sg : mindMapStore.listSubgraphs(tenantId)) {
            if (SubgraphTypes.GOAL.equals(sg.type())) {
                return sg.id();
            }
        }
        return null;
    }
}
