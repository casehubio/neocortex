package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.mindmap.MindMapEdge;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.MindMapSubgraph;
import io.casehub.neocortex.mindmap.intelligence.consolidation.ConsolidationCompleted;
import io.casehub.neocortex.mindmap.intelligence.consolidation.PhaseResult;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SnapshotCaptureService {

    private final SnapshotStore snapshotStore;
    private final MindMapStore mindMapStore;
    private final int keyframeInterval;
    private final int retentionDays;
    private volatile Instant lastPurgeTime = Instant.EPOCH;

    public SnapshotCaptureService(SnapshotStore snapshotStore, MindMapStore mindMapStore,
                                   int keyframeInterval, int retentionDays) {
        this.snapshotStore = snapshotStore;
        this.mindMapStore = mindMapStore;
        this.keyframeInterval = keyframeInterval;
        this.retentionDays = retentionDays;
    }

    public void onConsolidationCompleted(ConsolidationCompleted event) {
        String tenantId = event.tenantId();
        List<PhaseResult> phaseResults = event.phaseResults();

        var auditEntry = buildAuditEntry(tenantId, phaseResults);
        snapshotStore.storeAuditEntry(auditEntry);

        checkKeyframeThreshold(tenantId);
        checkPurge(tenantId);
    }

    private ConsolidationAuditEntry buildAuditEntry(String tenantId, List<PhaseResult> phaseResults) {
        Instant startedAt = phaseResults.isEmpty() ? Instant.now()
            : phaseResults.getFirst().startedAt();
        Instant completedAt = phaseResults.isEmpty() ? Instant.now()
            : phaseResults.getLast().completedAt();

        List<ConsolidationAuditEntry.PhaseAuditEntry> phases = phaseResults.stream()
            .map(pr -> {
                int mutationCount = countPhaseMutations(tenantId, pr);
                return new ConsolidationAuditEntry.PhaseAuditEntry(
                    pr.phaseName(),
                    Duration.between(pr.startedAt(), pr.completedAt()),
                    mutationCount,
                    pr.success(),
                    pr.errorMessage());
            })
            .toList();

        return new ConsolidationAuditEntry(tenantId, startedAt, completedAt, phases);
    }

    private int countPhaseMutations(String tenantId, PhaseResult pr) {
        return snapshotStore.findMutations(tenantId, null, pr.startedAt(), pr.completedAt())
            .stream()
            .filter(m -> m.source().equals("consolidation:" + pr.phaseName()))
            .mapToInt(m -> 1)
            .sum();
    }

    private void checkKeyframeThreshold(String tenantId) {
        if (mindMapStore == null) return;
        List<MindMapSubgraph> subgraphs = mindMapStore.listSubgraphs(tenantId);
        for (MindMapSubgraph sg : subgraphs) {
            long count = snapshotStore.mutationCountSinceKeyframe(tenantId, sg.id());
            if (count >= keyframeInterval) {
                captureKeyframe(tenantId, sg.id());
            }
        }
    }

    private void captureKeyframe(String tenantId, String subgraphId) {
        List<MindMapNode> nodes = mindMapStore.nodesIn(subgraphId, tenantId);
        Set<String> seenEdgeIds = new HashSet<>();
        List<MindMapEdge> edges = new java.util.ArrayList<>();
        for (MindMapNode node : nodes) {
            for (MindMapEdge edge : mindMapStore.neighbors(node.id(), tenantId)) {
                if (seenEdgeIds.add(edge.id())) {
                    edges.add(edge);
                }
            }
        }

        var keyframe = new GraphSnapshot(
            UUID.randomUUID().toString(),
            tenantId,
            subgraphId,
            Instant.now(),
            GraphSnapshot.SnapshotType.KEYFRAME,
            nodes.stream().map(NodeSnapshot::from).toList(),
            edges.stream().map(EdgeSnapshot::from).toList());
        snapshotStore.storeKeyframe(keyframe);
    }

    private void checkPurge(String tenantId) {
        Instant now = Instant.now();
        if (Duration.between(lastPurgeTime, now).toHours() >= 24) {
            snapshotStore.purge(new SnapshotRetentionPolicy(
                Duration.ofDays(retentionDays), tenantId));
            lastPurgeTime = now;
        }
    }
}
