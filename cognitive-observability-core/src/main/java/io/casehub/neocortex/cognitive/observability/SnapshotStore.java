package io.casehub.neocortex.cognitive.observability;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SnapshotStore {

    // --- Mutation storage ---
    void storeMutation(String tenantId, GraphMutation mutation);
    List<GraphMutation> findMutations(String tenantId, String subgraphId, Instant from, Instant to);
    List<GraphMutation> findMutationsForEntity(String tenantId, String nodeId, Instant from, Instant to);

    // --- Keyframe storage ---
    void storeKeyframe(GraphSnapshot keyframe);
    GraphSnapshot reconstruct(String tenantId, String subgraphId, Instant pointInTime);
    Optional<GraphSnapshot> latestKeyframe(String tenantId, String subgraphId);
    long mutationCountSinceKeyframe(String tenantId, String subgraphId);

    // --- Consolidation audit log ---
    void storeAuditEntry(ConsolidationAuditEntry entry);
    List<ConsolidationAuditEntry> findAuditEntries(String tenantId, Instant from, Instant to);
    Optional<Instant> lastConsolidationTime(String tenantId);

    // --- Lifecycle ---
    void purge(SnapshotRetentionPolicy policy);
    long count(String tenantId);
}
