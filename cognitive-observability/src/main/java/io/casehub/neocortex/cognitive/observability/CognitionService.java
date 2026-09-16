package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognitive.index.CognitiveProfile;
import io.casehub.neocortex.cognitive.index.CognitiveProfileQuery;
import io.casehub.neocortex.cognitive.index.EntityKnowledge;
import io.casehub.neocortex.mindmap.MindMapStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class CognitionService implements CognitionApi {

    private final MindMapStore store;
    private final Instance<CognitiveProfile> cognitiveProfile;
    private final Instance<SnapshotStore> snapshotStore;

    public CognitionService(MindMapStore store,
                            Instance<CognitiveProfile> cognitiveProfile,
                            Instance<SnapshotStore> snapshotStore) {
        this.store = store;
        this.cognitiveProfile = cognitiveProfile;
        this.snapshotStore = snapshotStore;
    }

    @Override
    public CognitionInspectResult inspect(String tenantId, String subgraphId) {
        return CognitionInspectService.inspect(store, tenantId, subgraphId);
    }

    @Override
    public EntityKnowledge entity(String tenantId, String entityName, String nodeId,
                                  String subgraphId, Boolean includeMemories, Integer memoryLimit) {
        if (!cognitiveProfile.isResolvable()) {
            return null;
        }
        CognitiveProfile profile = cognitiveProfile.get();
        CognitiveProfileQuery query;
        if (nodeId != null) {
            query = CognitiveProfileQuery.byId(nodeId, tenantId);
        } else if (subgraphId != null) {
            query = CognitiveProfileQuery.byName(entityName, subgraphId, tenantId);
        } else {
            query = CognitiveProfileQuery.byName(entityName, tenantId);
        }
        boolean doInclude = includeMemories == null || includeMemories;
        int limit = memoryLimit != null ? memoryLimit : 10;
        if (!doInclude) {
            query = query.withMemoryLimit(0);
        } else {
            query = query.withMemoryLimit(limit);
        }
        Optional<EntityKnowledge> result = profile.resolve(query);
        return result.orElse(null);
    }

    @Override
    public GraphHealthReport health(String tenantId, String subgraphId,
                                    Integer staleThresholdDays, Double lowConfidenceThreshold) {
        int staleDays = staleThresholdDays != null ? staleThresholdDays : 30;
        double confThreshold = lowConfidenceThreshold != null ? lowConfidenceThreshold : 0.3;
        return CognitionHealthService.health(store, tenantId, subgraphId, staleDays, confThreshold);
    }

    @Override
    public GraphDiffResult diff(String tenantId, String subgraphId,
                                String from, String to, String source) {
        if (!snapshotStore.isResolvable()) {
            return null;
        }
        Instant fromInstant = from != null ? Instant.parse(from) : null;
        Instant toInstant = to != null ? Instant.parse(to) : null;
        return CognitionDiffService.diff(snapshotStore.get(), tenantId, subgraphId,
                                         fromInstant, toInstant, source);
    }

    @Override
    public EntityTrace trace(String tenantId, String entityName, String nodeId,
                             String subgraphId, String from, String to) {
        if (!snapshotStore.isResolvable()) {
            return null;
        }
        String resolvedNodeId = nodeId;
        if (resolvedNodeId == null && entityName != null) {
            var node = subgraphId != null
                       ? store.resolveNode(entityName, subgraphId, tenantId)
                       : store.resolveNode(entityName, null, tenantId);
            if (node != null) {
                resolvedNodeId = node.id();
            }
        }
        if (resolvedNodeId == null) {
            return new EntityTrace(nodeId, entityName, java.util.List.of());
        }
        Instant fromInstant = from != null ? Instant.parse(from) : null;
        Instant toInstant = to != null ? Instant.parse(to) : null;
        return CognitionTraceService.trace(snapshotStore.get(), tenantId,
                                           resolvedNodeId, fromInstant, toInstant);
    }
}
