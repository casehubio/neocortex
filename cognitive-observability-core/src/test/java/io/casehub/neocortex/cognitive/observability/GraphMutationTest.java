package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphMutationTest {

    @Test
    void shouldPatternMatchAllMutationTypes() {
        GraphMutation mutation = new GraphMutation.NodeAdded(
            "n1", "Alice", "sg1", new Confidence(ConfidenceOrigin.STATED, 0.9, null),
            Instant.now(), "manual");

        String type = switch (mutation) {
            case GraphMutation.NodeAdded na -> "added:" + na.name();
            case GraphMutation.NodeUpdated nu -> "updated:" + nu.nodeId();
            case GraphMutation.NodeErased ne -> "erased:" + ne.nodeId();
            case GraphMutation.EdgeAdded ea -> "edge:" + ea.edgeType();
            case GraphMutation.EdgeRemoved er -> "edge-rm:" + er.edgeId();
            case GraphMutation.NodesMerged nm -> "merged:" + nm.survivorId();
            case GraphMutation.NodeSuperseded ns -> "super:" + ns.supersededId();
            case GraphMutation.NodeReinstated nr -> "reinstate:" + nr.nodeId();
            case GraphMutation.AliasAdded aa -> "alias:" + aa.alias();
            case GraphMutation.AliasRemoved ar -> "alias-rm:" + ar.alias();
            case GraphMutation.SubgraphCreated sc -> "sg:" + sc.name();
            case GraphMutation.SubgraphErased se -> "sg-rm:" + se.subgraphId();
            case GraphMutation.EntityErased ee -> "entity-rm:" + ee.entityName();
        };

        assertEquals("added:Alice", type);
        assertEquals("manual", mutation.source());
        assertNotNull(mutation.timestamp());
    }

    @Test
    void shouldAccessCommonFieldsViaInterface() {
        Instant now = Instant.now();
        GraphMutation m1 = new GraphMutation.EdgeAdded("e1", "n1", "n2", "knows",
            new Confidence(ConfidenceOrigin.STATED, 0.9, null), now, "extraction");
        GraphMutation m2 = new GraphMutation.NodeReinstated("n1", now, "consolidation:Phase1");

        assertEquals(now, m1.timestamp());
        assertEquals("extraction", m1.source());
        assertEquals("consolidation:Phase1", m2.source());
    }

    @Test
    void shouldCreateAllVariantsWithoutError() {
        Instant now = Instant.now();
        var confidence = new Confidence(ConfidenceOrigin.STATED, 0.9, null);

        List<GraphMutation> mutations = List.of(
            new GraphMutation.NodeAdded("n1", "Alice", "sg1", confidence, now, "test"),
            new GraphMutation.NodeUpdated("n1", "sg1", Map.of("name", new FieldChange("name", "A", "B")), now, "test"),
            new GraphMutation.NodeErased("n1", "sg1", 3, now, "test"),
            new GraphMutation.EdgeAdded("e1", "n1", "n2", "knows", confidence, now, "test"),
            new GraphMutation.EdgeRemoved("e1", "n1", "n2", "knows", now, "test"),
            new GraphMutation.NodesMerged("n1", "n2", List.of(), now, "test"),
            new GraphMutation.NodeSuperseded("n1", "n2", "duplicate", now, "test"),
            new GraphMutation.NodeReinstated("n1", now, "test"),
            new GraphMutation.AliasAdded("n1", "ali", now, "test"),
            new GraphMutation.AliasRemoved("n1", "ali", now, "test"),
            new GraphMutation.SubgraphCreated("sg1", "Test", "concept", now, "test"),
            new GraphMutation.SubgraphErased("sg1", 5, now, "test"),
            new GraphMutation.EntityErased("Alice", 2, now, "test")
        );

        assertEquals(13, mutations.size());
        for (var m : mutations) {
            assertEquals(now, m.timestamp());
            assertEquals("test", m.source());
        }
    }
}
