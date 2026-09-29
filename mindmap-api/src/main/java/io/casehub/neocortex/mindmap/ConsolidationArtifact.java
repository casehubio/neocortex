package io.casehub.neocortex.mindmap;

import java.time.Instant;

public sealed interface ConsolidationArtifact {
    String tenantId();
    Instant timestamp();

    record GraduatedExperience(String tenantId, String nodeId, String name,
            String cognitiveKind, double graduationScore, String agentId,
            String eventType, Instant timestamp) implements ConsolidationArtifact {}

    record MergePerformed(String tenantId, String keepNodeId,
            String removedNodeId, double score, String reason,
            Instant timestamp) implements ConsolidationArtifact {}

    record MergeFlagged(String tenantId, String nodeId1, String nodeId2,
            double score, String reason,
            Instant timestamp) implements ConsolidationArtifact {}

    record CuriosityQuestion(String tenantId, String targetNodeId,
            String targetSubgraphId, String question, String description,
            double score, Instant timestamp) implements ConsolidationArtifact {}

    record CommunitySummaryCreated(String tenantId, String summaryNodeId,
            String subgraphId, String title, int memberCount,
            Instant timestamp) implements ConsolidationArtifact {}
}
