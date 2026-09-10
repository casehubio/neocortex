package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeUpdate;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Priority(10)
public class AccessFrequencyPhase implements ConsolidationPhase {

    private final MindMapStore store;
    private final RetrievalAccessTracker tracker;

    @Inject
    public AccessFrequencyPhase(MindMapStore store, RetrievalAccessTracker tracker) {
        this.store = store;
        this.tracker = tracker;
    }

    @Override
    public String name() {
        return "access-frequency";
    }

    private AccessSnapshot cachedSnapshot;
    private long lastSnapshotTick;
    private long currentTick;

    void beginTick() {
        currentTick++;
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (lastSnapshotTick < currentTick) {
            cachedSnapshot = tracker.swapAndReset();
            lastSnapshotTick = currentTick;
        }
        var snapshot = cachedSnapshot;
        if (snapshot == null || snapshot.counts().isEmpty()) return;

        for (var entry : snapshot.counts().entrySet()) {
            String nodeId = entry.getKey();
            long increment = entry.getValue();
            try {
                var node = store.getNode(nodeId, tenantId);
                if (node == null) continue;
                int existing = node.property("storageStrength")
                    .map(Integer::parseInt).orElse(0);
                int newStrength = existing + (int) increment;
                String lastAccessed = snapshot.lastAccessTimes()
                    .containsKey(nodeId)
                    ? snapshot.lastAccessTimes().get(nodeId).toString()
                    : java.time.Instant.now().toString();
                store.updateNode(nodeId,
                    NodeUpdate.empty().withPropertiesToSet(Map.of(
                        "storageStrength", String.valueOf(newStrength),
                        "lastAccessed", lastAccessed)),
                    tenantId);
            } catch (Exception e) {
                // node may have been erased between snapshot and flush
            }
        }
    }
}
