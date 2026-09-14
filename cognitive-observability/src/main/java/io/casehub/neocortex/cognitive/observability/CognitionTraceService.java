package io.casehub.neocortex.cognitive.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class CognitionTraceService {

    private CognitionTraceService() {}

    public static EntityTrace trace(SnapshotStore snapshotStore,
                                    String tenantId, String nodeId,
                                    Instant from, Instant to) {
        Instant resolvedFrom = from != null ? from : Instant.EPOCH;
        Instant resolvedTo = to != null ? to : Instant.now().plus(Duration.ofSeconds(1));

        List<GraphMutation> mutations = snapshotStore.findMutationsForEntity(
            tenantId, nodeId, resolvedFrom, resolvedTo);

        List<TraceEvent> events = mutations.stream()
            .map(m -> classifyMutation(m, nodeId))
            .toList();

        String entityName = resolveEntityName(mutations, nodeId);

        return new EntityTrace(nodeId, entityName, events);
    }

    private static TraceEvent classifyMutation(GraphMutation mutation, String nodeId) {
        return switch (mutation) {
            case GraphMutation.NodeAdded na ->
                new TraceEvent(mutation, TraceEvent.Type.CREATED, List.of());

            case GraphMutation.NodeUpdated nu ->
                new TraceEvent(mutation, TraceEvent.Type.UPDATED, List.of());

            case GraphMutation.NodeErased ne ->
                new TraceEvent(mutation, TraceEvent.Type.ERASED, List.of());

            case GraphMutation.NodesMerged nm -> {
                if (nm.absorbedId().equals(nodeId)) {
                    yield new TraceEvent(mutation, TraceEvent.Type.MERGED_INTO, List.of(nm.survivorId()));
                } else {
                    yield new TraceEvent(mutation, TraceEvent.Type.MERGED_FROM, List.of(nm.absorbedId()));
                }
            }

            case GraphMutation.NodeSuperseded ns -> {
                if (ns.supersededId().equals(nodeId)) {
                    yield new TraceEvent(mutation, TraceEvent.Type.SUPERSEDED, List.of(ns.supersedingId()));
                } else {
                    yield new TraceEvent(mutation, TraceEvent.Type.SUPERSEDED_BY, List.of(ns.supersededId()));
                }
            }

            case GraphMutation.NodeReinstated nr ->
                new TraceEvent(mutation, TraceEvent.Type.REINSTATED, List.of());

            case GraphMutation.AliasAdded aa ->
                new TraceEvent(mutation, TraceEvent.Type.ALIAS_ADDED, List.of());

            case GraphMutation.AliasRemoved ar ->
                new TraceEvent(mutation, TraceEvent.Type.ALIAS_REMOVED, List.of());

            case GraphMutation.EdgeAdded ea -> {
                String other = ea.sourceNodeId().equals(nodeId) ? ea.targetNodeId() : ea.sourceNodeId();
                yield new TraceEvent(mutation, TraceEvent.Type.UPDATED, List.of(other));
            }

            case GraphMutation.EdgeRemoved er -> {
                String other = er.sourceNodeId().equals(nodeId) ? er.targetNodeId() : er.sourceNodeId();
                yield new TraceEvent(mutation, TraceEvent.Type.UPDATED, List.of(other));
            }

            case GraphMutation.SubgraphCreated ignored ->
                new TraceEvent(mutation, TraceEvent.Type.UPDATED, List.of());

            case GraphMutation.SubgraphErased ignored ->
                new TraceEvent(mutation, TraceEvent.Type.ERASED, List.of());

            case GraphMutation.EntityErased ignored ->
                new TraceEvent(mutation, TraceEvent.Type.ERASED, List.of());
        };
    }

    private static String resolveEntityName(List<GraphMutation> mutations, String nodeId) {
        for (GraphMutation m : mutations) {
            if (m instanceof GraphMutation.NodeAdded na && na.nodeId().equals(nodeId)) {
                return na.name();
            }
        }
        return null;
    }
}
