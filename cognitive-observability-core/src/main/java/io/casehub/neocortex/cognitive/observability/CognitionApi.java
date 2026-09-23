package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognitive.index.EntityKnowledge;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformQuery;

@McpDomain(value = "cognition", app = "neocortex")
public interface CognitionApi {

    @PlatformQuery("Aggregate stats: node/edge counts per subgraph, confidence distribution, trait summary")
    CognitionInspectResult inspect(String tenantId, String subgraphId);

    @PlatformQuery("Entity deep dive: node, edges, traits, PAD, confidence, affect trajectory, related memories")
    EntityKnowledge entity(String tenantId, String entityName, String nodeId,
                           String subgraphId, Boolean includeMemories, Integer memoryLimit);

    @PlatformQuery("Graph health: orphans, contradictions, low-confidence clusters, unvalidated edges, stale nodes")
    GraphHealthReport health(String tenantId, String subgraphId,
                             Integer staleThresholdDays, Double lowConfidenceThreshold);

    @PlatformQuery("Structured delta: what changed between two points in time")
    GraphDiffResult diff(String tenantId, String subgraphId,
                         String from, String to, String source);

    @PlatformQuery("Entity audit trail: creation, updates, merges, supersessions over time")
    EntityTrace trace(String tenantId, String entityName, String nodeId,
                      String subgraphId, String from, String to);
}
