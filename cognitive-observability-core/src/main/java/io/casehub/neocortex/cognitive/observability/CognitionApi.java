package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognitive.index.EntityKnowledge;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformQuery;

@McpDomain(value = "neocortex/cognition", app = "neocortex", summary = "Cognitive observability — reasoning traces and decision transparency")
public interface CognitionApi {

    @PlatformQuery("Aggregate stats: node/edge counts per subgraph, confidence distribution, trait summary")
    CognitionInspectResult inspect(@ContextParam("tenancyId") String tenancyId, String subgraphId);

    @PlatformQuery("Entity deep dive: node, edges, traits, PAD, confidence, affect trajectory, related memories. "
            + "Provide entityName for human-friendly lookup or nodeId for precise UUID lookup — either is sufficient")
    EntityKnowledge entity(@ContextParam("tenancyId") String tenancyId, String entityName, String nodeId,
                           String subgraphId, Boolean includeMemories, Integer memoryLimit);

    @PlatformQuery("Graph health: orphans, contradictions, low-confidence clusters, unvalidated edges, stale nodes")
    GraphHealthReport health(@ContextParam("tenancyId") String tenancyId, String subgraphId,
                             Integer staleThresholdDays, Double lowConfidenceThreshold);

    @PlatformQuery("Structured delta: what changed between two points in time. "
            + "from/to are ISO-8601 timestamps (e.g. 2026-01-01T00:00:00Z). source filters by mutation source")
    GraphDiffResult diff(@ContextParam("tenancyId") String tenancyId, String subgraphId,
                         String from, String to, String source);

    @PlatformQuery("Entity audit trail: creation, updates, merges, supersessions over time. "
            + "from/to are ISO-8601 timestamps to bound the time range. entityName or nodeId — either is sufficient")
    EntityTrace trace(@ContextParam("tenancyId") String tenancyId, String entityName, String nodeId,
                      String subgraphId, String from, String to);

    @PlatformQuery("BFS graph traversal from a focus node with depth limiting and confidence filtering")
    GraphTraversalResult graph(@ContextParam("tenancyId") String tenancyId, String nodeId, Integer maxDepth,
                               Double minConfidence, String subgraphId);

    @PlatformQuery("Affect trajectory: PAD slope, volatility, and trend for an entity or overall mood")
    io.casehub.neocortex.cognitive.index.AffectTrajectory affect(@ContextParam("tenancyId") String tenancyId,
                                                                 String entityName, String nodeId, String subgraphId);

    @PlatformQuery("Ranked attention signals for an agent")
    io.casehub.neocortex.mindmap.AttentionBriefing attention(@ContextParam("tenancyId") String tenancyId, String principalId, Integer topN);

    @PlatformQuery("Cross-subgraph DTW correlation between affect trajectories. "
            + "from/to are ISO-8601 timestamps bounding the correlation window")
    io.casehub.neocortex.cognitive.index.DomainActivationResult domainActivation(
            @ContextParam("tenancyId") String tenancyId, String principalId, String subgraph1, String subgraph2,
            String from, String to);

    @PlatformQuery("Graph structure analytics: centrality, k-cores, density, orphans, contradictions")
    GraphAnalyticsResult analytics(@ContextParam("tenancyId") String tenancyId, String subgraphId,
                                   Integer kCoreK, Integer staleThresholdDays, Double lowConfidenceThreshold);

    @PlatformQuery("Activity queries — queryType selects the query: "
            + "lastSeenWith (personName required), activitiesWithPerson (personName required), "
            + "placesVisited (personName required), activitiesAtPlace (placeName required)")
    java.util.List<io.casehub.neocortex.mindmap.intelligence.ActivitySummary> activities(
            @ContextParam("tenancyId") String tenancyId, String queryType, String personName, String placeName, Integer limit);
}
