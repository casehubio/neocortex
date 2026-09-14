package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FieldChangeTest {

    @Test
    void shouldDiffNameChange() {
        var store = new InMemoryMindMapStore();
        String sgId = store.createSubgraph(new SubgraphInput("test", "concept", null), "t1");
        String nodeId = store.addNode(NodeInput.of("Alice", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        var before = store.getNode(nodeId, "t1");

        var update = NodeUpdate.empty().withName("Alicia");
        Map<String, FieldChange> changes = FieldChange.diff(before, update);

        assertTrue(changes.containsKey("name"));
        assertEquals("Alice", changes.get("name").oldValue());
        assertEquals("Alicia", changes.get("name").newValue());
    }

    @Test
    void shouldDiffConfidenceChange() {
        var store = new InMemoryMindMapStore();
        String sgId = store.createSubgraph(new SubgraphInput("test", "concept", null), "t1");
        String nodeId = store.addNode(NodeInput.of("Alice", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        var before = store.getNode(nodeId, "t1");

        var newConf = new Confidence(ConfidenceOrigin.INFERRED, 0.5, null);
        var update = NodeUpdate.empty().withConfidence(newConf);
        Map<String, FieldChange> changes = FieldChange.diff(before, update);

        assertTrue(changes.containsKey("confidence"));
    }

    @Test
    void shouldDiffTraitChanges() {
        var store = new InMemoryMindMapStore();
        String sgId = store.createSubgraph(new SubgraphInput("test", "concept", null), "t1");
        String nodeId = store.addNode(NodeInput.of("Alice", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null))
            .withTraits(Set.of("Personable")), "t1");
        var before = store.getNode(nodeId, "t1");

        var update = NodeUpdate.empty().withTraitsToAdd(Set.of("Projectlike"));
        Map<String, FieldChange> changes = FieldChange.diff(before, update);

        assertTrue(changes.containsKey("traits"));
        @SuppressWarnings("unchecked")
        Set<String> newTraits = (Set<String>) changes.get("traits").newValue();
        assertTrue(newTraits.contains("Personable"));
        assertTrue(newTraits.contains("Projectlike"));
    }

    @Test
    void shouldIgnoreNullFields() {
        var store = new InMemoryMindMapStore();
        String sgId = store.createSubgraph(new SubgraphInput("test", "concept", null), "t1");
        String nodeId = store.addNode(NodeInput.of("Alice", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");
        var before = store.getNode(nodeId, "t1");

        var update = NodeUpdate.empty();
        Map<String, FieldChange> changes = FieldChange.diff(before, update);

        assertTrue(changes.isEmpty());
    }

    @Test
    void shouldDiffPropertyChanges() {
        var store = new InMemoryMindMapStore();
        String sgId = store.createSubgraph(new SubgraphInput("test", "concept", null), "t1");
        String nodeId = store.addNode(NodeInput.of("Alice", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null))
            .withProperties(Map.of("role", "engineer")), "t1");
        var before = store.getNode(nodeId, "t1");

        var update = NodeUpdate.empty()
            .withPropertiesToSet(Map.of("role", "manager"))
            .withPropertiesToRemove(Set.of());
        Map<String, FieldChange> changes = FieldChange.diff(before, update);

        assertTrue(changes.containsKey("properties"));
    }

    @Test
    void shouldReturnEmptyForNullBefore() {
        var update = NodeUpdate.empty().withName("Alice");
        Map<String, FieldChange> changes = FieldChange.diff(null, update);
        assertTrue(changes.isEmpty());
    }
}
