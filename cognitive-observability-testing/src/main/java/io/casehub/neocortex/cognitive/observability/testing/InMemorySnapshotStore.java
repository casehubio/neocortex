package io.casehub.neocortex.cognitive.observability.testing;

import io.casehub.neocortex.cognitive.observability.ConsolidationAuditEntry;
import io.casehub.neocortex.cognitive.observability.GraphMutation;
import io.casehub.neocortex.cognitive.observability.GraphSnapshot;
import io.casehub.neocortex.cognitive.observability.SnapshotRetentionPolicy;
import io.casehub.neocortex.cognitive.observability.SnapshotStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemorySnapshotStore implements SnapshotStore {

    private record StoredMutation(String id, String tenantId, GraphMutation mutation, Set<String> nodeIds) {}

    private final List<StoredMutation> mutations = new CopyOnWriteArrayList<>();
    private final List<GraphSnapshot> keyframes = new CopyOnWriteArrayList<>();
    private final List<ConsolidationAuditEntry> auditEntries = new CopyOnWriteArrayList<>();

    @Override
    public void storeMutation(String tenantId, GraphMutation mutation) {
        Set<String> nodeIds = extractNodeIds(mutation);
        mutations.add(new StoredMutation(UUID.randomUUID().toString(), tenantId, mutation, nodeIds));
    }

    @Override
    public List<GraphMutation> findMutations(String tenantId, String subgraphId, Instant from, Instant to) {
        return mutations.stream()
            .filter(m -> m.tenantId.equals(tenantId))
            .filter(m -> subgraphId == null || subgraphIdOf(m.mutation).equals(subgraphId) || subgraphIdOf(m.mutation) == null)
            .filter(m -> !m.mutation.timestamp().isBefore(from))
            .filter(m -> !m.mutation.timestamp().isAfter(to))
            .sorted(Comparator.comparing(m -> m.mutation.timestamp()))
            .map(m -> m.mutation)
            .toList();
    }

    @Override
    public List<GraphMutation> findMutationsForEntity(String tenantId, String nodeId, Instant from, Instant to) {
        return mutations.stream()
            .filter(m -> m.tenantId.equals(tenantId))
            .filter(m -> m.nodeIds.contains(nodeId))
            .filter(m -> !m.mutation.timestamp().isBefore(from))
            .filter(m -> !m.mutation.timestamp().isAfter(to))
            .sorted(Comparator.comparing(m -> m.mutation.timestamp()))
            .map(m -> m.mutation)
            .toList();
    }

    @Override
    public void storeKeyframe(GraphSnapshot keyframe) {
        keyframes.add(keyframe);
    }

    @Override
    public GraphSnapshot reconstruct(String tenantId, String subgraphId, Instant pointInTime) {
        var keyframe = keyframes.stream()
            .filter(k -> k.tenantId().equals(tenantId) && k.subgraphId().equals(subgraphId))
            .filter(k -> !k.capturedAt().isAfter(pointInTime))
            .max(Comparator.comparing(GraphSnapshot::capturedAt))
            .orElse(null);
        return keyframe;
    }

    @Override
    public Optional<GraphSnapshot> latestKeyframe(String tenantId, String subgraphId) {
        return keyframes.stream()
            .filter(k -> k.tenantId().equals(tenantId) && k.subgraphId().equals(subgraphId))
            .max(Comparator.comparing(GraphSnapshot::capturedAt));
    }

    @Override
    public long mutationCountSinceKeyframe(String tenantId, String subgraphId) {
        Optional<Instant> lastKeyframeTime = latestKeyframe(tenantId, subgraphId)
            .map(GraphSnapshot::capturedAt);
        Instant since = lastKeyframeTime.orElse(Instant.EPOCH);
        return mutations.stream()
            .filter(m -> m.tenantId.equals(tenantId))
            .filter(m -> subgraphId == null || Objects.equals(subgraphIdOf(m.mutation), subgraphId))
            .filter(m -> m.mutation.timestamp().isAfter(since))
            .count();
    }

    @Override
    public void storeAuditEntry(ConsolidationAuditEntry entry) {
        auditEntries.add(entry);
    }

    @Override
    public List<ConsolidationAuditEntry> findAuditEntries(String tenantId, Instant from, Instant to) {
        return auditEntries.stream()
            .filter(e -> e.tenantId().equals(tenantId))
            .filter(e -> !e.completedAt().isBefore(from) && !e.completedAt().isAfter(to))
            .sorted(Comparator.comparing(ConsolidationAuditEntry::completedAt))
            .toList();
    }

    @Override
    public Optional<Instant> lastConsolidationTime(String tenantId) {
        return auditEntries.stream()
            .filter(e -> e.tenantId().equals(tenantId))
            .map(ConsolidationAuditEntry::completedAt)
            .max(Instant::compareTo);
    }

    @Override
    public void purge(SnapshotRetentionPolicy policy) {
        Instant cutoff = Instant.now().minus(policy.maxAge());
        if (policy.tenantId() != null) {
            mutations.removeIf(m -> m.tenantId.equals(policy.tenantId()) && m.mutation.timestamp().isBefore(cutoff));
            keyframes.removeIf(k -> k.tenantId().equals(policy.tenantId()) && k.capturedAt().isBefore(cutoff));
            auditEntries.removeIf(e -> e.tenantId().equals(policy.tenantId()) && e.completedAt().isBefore(cutoff));
        } else {
            mutations.removeIf(m -> m.mutation.timestamp().isBefore(cutoff));
            keyframes.removeIf(k -> k.capturedAt().isBefore(cutoff));
            auditEntries.removeIf(e -> e.completedAt().isBefore(cutoff));
        }
    }

    @Override
    public long count(String tenantId) {
        return mutations.stream().filter(m -> m.tenantId.equals(tenantId)).count()
            + keyframes.stream().filter(k -> k.tenantId().equals(tenantId)).count();
    }

    private static Set<String> extractNodeIds(GraphMutation mutation) {
        return switch (mutation) {
            case GraphMutation.NodeAdded na -> Set.of(na.nodeId());
            case GraphMutation.NodeUpdated nu -> Set.of(nu.nodeId());
            case GraphMutation.NodeErased ne -> Set.of(ne.nodeId());
            case GraphMutation.EdgeAdded ea -> Set.of(ea.sourceNodeId(), ea.targetNodeId());
            case GraphMutation.EdgeRemoved er -> Set.of(er.sourceNodeId(), er.targetNodeId());
            case GraphMutation.NodesMerged nm -> Set.of(nm.survivorId(), nm.absorbedId());
            case GraphMutation.NodeSuperseded ns -> Set.of(ns.supersededId(), ns.supersedingId());
            case GraphMutation.NodeReinstated nr -> Set.of(nr.nodeId());
            case GraphMutation.AliasAdded aa -> Set.of(aa.nodeId());
            case GraphMutation.AliasRemoved ar -> Set.of(ar.nodeId());
            case GraphMutation.SubgraphCreated sc -> Set.of();
            case GraphMutation.SubgraphErased se -> Set.of();
            case GraphMutation.EntityErased ee -> Set.of();
        };
    }

    private static String subgraphIdOf(GraphMutation mutation) {
        return switch (mutation) {
            case GraphMutation.NodeAdded na -> na.subgraphId();
            case GraphMutation.NodeUpdated nu -> nu.subgraphId();
            case GraphMutation.NodeErased ne -> ne.subgraphId();
            case GraphMutation.SubgraphCreated sc -> sc.subgraphId();
            case GraphMutation.SubgraphErased se -> se.subgraphId();
            default -> null;
        };
    }
}
