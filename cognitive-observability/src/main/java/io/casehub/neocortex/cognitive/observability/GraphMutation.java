package io.casehub.neocortex.cognitive.observability;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.mindmap.MergeConflict;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = GraphMutation.NodeAdded.class, name = "NodeAdded"),
    @JsonSubTypes.Type(value = GraphMutation.NodeUpdated.class, name = "NodeUpdated"),
    @JsonSubTypes.Type(value = GraphMutation.NodeErased.class, name = "NodeErased"),
    @JsonSubTypes.Type(value = GraphMutation.EdgeAdded.class, name = "EdgeAdded"),
    @JsonSubTypes.Type(value = GraphMutation.EdgeRemoved.class, name = "EdgeRemoved"),
    @JsonSubTypes.Type(value = GraphMutation.NodesMerged.class, name = "NodesMerged"),
    @JsonSubTypes.Type(value = GraphMutation.NodeSuperseded.class, name = "NodeSuperseded"),
    @JsonSubTypes.Type(value = GraphMutation.NodeReinstated.class, name = "NodeReinstated"),
    @JsonSubTypes.Type(value = GraphMutation.AliasAdded.class, name = "AliasAdded"),
    @JsonSubTypes.Type(value = GraphMutation.AliasRemoved.class, name = "AliasRemoved"),
    @JsonSubTypes.Type(value = GraphMutation.SubgraphCreated.class, name = "SubgraphCreated"),
    @JsonSubTypes.Type(value = GraphMutation.SubgraphErased.class, name = "SubgraphErased"),
    @JsonSubTypes.Type(value = GraphMutation.EntityErased.class, name = "EntityErased"),
})
public sealed interface GraphMutation {

    Instant timestamp();
    String source();

    // --- Node mutations ---

    record NodeAdded(String nodeId, String name, String subgraphId,
                     Confidence confidence, Instant timestamp, String source) implements GraphMutation {}

    record NodeUpdated(String nodeId, String subgraphId, Map<String, FieldChange> changes,
                       Instant timestamp, String source) implements GraphMutation {}

    record NodeErased(String nodeId, String subgraphId, int cascadedCount,
                      Instant timestamp, String source) implements GraphMutation {}

    // --- Edge mutations ---

    record EdgeAdded(String edgeId, String sourceNodeId, String targetNodeId,
                     String edgeType, Confidence confidence,
                     Instant timestamp, String source) implements GraphMutation {}

    record EdgeRemoved(String edgeId, String sourceNodeId, String targetNodeId,
                       String edgeType, Instant timestamp, String source) implements GraphMutation {}

    // --- Merge / supersession ---

    record NodesMerged(String survivorId, String absorbedId,
                       List<MergeConflict> conflictsResolved,
                       Instant timestamp, String source) implements GraphMutation {}

    record NodeSuperseded(String supersededId, String supersedingId,
                          String reason, Instant timestamp, String source) implements GraphMutation {}

    record NodeReinstated(String nodeId, Instant timestamp, String source) implements GraphMutation {}

    // --- Alias mutations ---

    record AliasAdded(String nodeId, String alias,
                      Instant timestamp, String source) implements GraphMutation {}

    record AliasRemoved(String nodeId, String alias,
                        Instant timestamp, String source) implements GraphMutation {}

    // --- Subgraph mutations ---

    record SubgraphCreated(String subgraphId, String name, String type,
                           Instant timestamp, String source) implements GraphMutation {}

    record SubgraphErased(String subgraphId, int nodesErased,
                          Instant timestamp, String source) implements GraphMutation {}

    // --- Bulk erasure ---

    record EntityErased(String entityName, int nodesAffected,
                        Instant timestamp, String source) implements GraphMutation {}
}
