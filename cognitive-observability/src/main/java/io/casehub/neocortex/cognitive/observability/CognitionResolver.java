package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognitive.index.CognitiveProfile;
import io.casehub.neocortex.cognitive.index.CognitiveProfileQuery;
import io.casehub.neocortex.cognitive.index.EntityKnowledge;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformQuery;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.graphql.DefaultValue;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

import java.util.Optional;

@McpDomain("cognition")
@GraphQLApi
public class CognitionResolver {

    @Inject
    MindMapStore store;

    @Inject
    Instance<CognitiveProfile> cognitiveProfile;

    @Query
    @PlatformQuery("Aggregate stats: node/edge counts per subgraph, confidence distribution, trait summary")
    public CognitionInspectResult inspect(@Name("tenantId") String tenantId,
                                           @Name("subgraphId") String subgraphId) {
        return CognitionInspectService.inspect(store, tenantId, subgraphId);
    }

    @Query
    @PlatformQuery("Entity deep dive: node, edges, traits, PAD, confidence, affect trajectory, related memories")
    public EntityKnowledge entity(@Name("tenantId") String tenantId,
                                   @Name("entityName") String entityName,
                                   @Name("nodeId") String nodeId,
                                   @Name("subgraphId") String subgraphId,
                                   @Name("includeMemories") @DefaultValue("true") boolean includeMemories,
                                   @Name("memoryLimit") @DefaultValue("10") int memoryLimit) {
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
        if (!includeMemories) {
            query = query.withMemoryLimit(0);
        } else {
            query = query.withMemoryLimit(memoryLimit);
        }
        Optional<EntityKnowledge> result = profile.resolve(query);
        return result.orElse(null);
    }

    @Query
    @PlatformQuery("Graph health: orphans, contradictions, low-confidence clusters, unvalidated edges, stale nodes")
    public GraphHealthReport health(@Name("tenantId") String tenantId,
                                     @Name("subgraphId") String subgraphId,
                                     @Name("staleThresholdDays") @DefaultValue("30") int staleThresholdDays,
                                     @Name("lowConfidenceThreshold") @DefaultValue("0.3") double lowConfidenceThreshold) {
        return CognitionHealthService.health(store, tenantId, subgraphId, staleThresholdDays, lowConfidenceThreshold);
    }
}
