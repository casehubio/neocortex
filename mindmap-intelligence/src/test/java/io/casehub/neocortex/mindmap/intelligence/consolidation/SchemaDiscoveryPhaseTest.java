package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.mindmap.intelligence.Personable;
import io.casehub.neocortex.mindmap.intelligence.TypeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class SchemaDiscoveryPhaseTest {

    private InMemoryMindMapStore store;
    private TypeRegistry registry;
    private SchemaDiscoveryPhase phase;
    private String sgId;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        registry = new TypeRegistry(store);
        phase = new SchemaDiscoveryPhase(store, registry, 0.8, 5, 0.95);
        sgId = store.createSubgraph(
            new SubgraphInput("Meetings", "meeting", null), "t1");
        registry.registerType("meeting", null, "t1");
    }

    @Test
    void discoversSchema_whenPropertyFrequencyAboveThreshold() {
        for (int i = 0; i < 5; i++) {
            store.addNode(NodeInput.of("meeting-" + i, sgId)
                .withProperties(Map.of("date", "2026-01-0" + (i + 1),
                                       "agenda", "topic-" + i)), "t1");
        }
        phase.run("t1", List.of("meeting"));

        Map<String, SchemaField> schema = registry.schemaFor("meeting", "t1");
        assertThat(schema).containsKey("date");
        assertThat(schema).containsKey("agenda");
        assertThat(schema.get("date").type()).isEqualTo("string");
    }

    @Test
    void skipsProperty_belowThreshold() {
        for (int i = 0; i < 5; i++) {
            Map<String, String> props = new HashMap<>(Map.of("date", "2026-01-01"));
            if (i < 3) props.put("location", "room-" + i);
            store.addNode(NodeInput.of("meeting-" + i, sgId)
                .withProperties(props), "t1");
        }
        phase.run("t1", List.of("meeting"));

        Map<String, SchemaField> schema = registry.schemaFor("meeting", "t1");
        assertThat(schema).containsKey("date");
        assertThat(schema).doesNotContainKey("location");
    }

    @Test
    void skipsType_belowMinSamples() {
        for (int i = 0; i < 4; i++) {
            store.addNode(NodeInput.of("meeting-" + i, sgId)
                .withProperties(Map.of("date", "2026-01-01")), "t1");
        }
        phase.run("t1", List.of("meeting"));

        Map<String, SchemaField> schema = registry.schemaFor("meeting", "t1");
        assertThat(schema).isEmpty();
    }

    @Test
    void setsRequired_whenFrequencyAboveRequiredThreshold() {
        for (int i = 0; i < 5; i++) {
            store.addNode(NodeInput.of("meeting-" + i, sgId)
                .withProperties(Map.of("date", "2026-01-0" + (i + 1))), "t1");
        }
        phase.run("t1", List.of("meeting"));

        SchemaField date = registry.schemaFor("meeting", "t1").get("date");
        assertThat(date).isNotNull();
        assertThat(date.required()).isTrue();
    }

    @Test
    void doesNotOverwrite_javaSourcedFields() {
        registry.registerType("person", null, Personable.class, "t1");
        String personSgId = store.createSubgraph(
            new SubgraphInput("People", SubgraphTypes.PERSON, null), "t1");
        for (int i = 0; i < 5; i++) {
            store.addNode(NodeInput.of("person-" + i, personSgId)
                .withProperties(Map.of("role", "engineer", "department", "eng")), "t1");
        }
        phase.run("t1", List.of(SubgraphTypes.PERSON));

        Map<String, SchemaField> schema = registry.schemaFor("person", "t1");
        assertThat(schema).containsKey("department");
        MindMapNode typeNode = store.search(MindMapQuery.of("t1", 100)
            .withType(SubgraphTypes.TYPE_SYSTEM)).stream()
            .filter(n -> n.name().equals("person")).findFirst().orElseThrow();
        assertThat(typeNode.property("schema.role.source")).hasValue("java");
        assertThat(typeNode.property("schema.department.source")).hasValue("discovered");
    }

    @Test
    void infersNumericType_whenAllValuesParseAsNumbers() {
        for (int i = 0; i < 5; i++) {
            store.addNode(NodeInput.of("meeting-" + i, sgId)
                .withProperties(Map.of("duration", String.valueOf(30 + i * 5))), "t1");
        }
        phase.run("t1", List.of("meeting"));

        SchemaField duration = registry.schemaFor("meeting", "t1").get("duration");
        assertThat(duration).isNotNull();
        assertThat(duration.type()).isEqualTo("number");
    }

    @Test
    void infersEnumValues_whenFewDistinctValues() {
        String[] statuses = {"active", "completed", "cancelled", "active", "active"};
        for (int i = 0; i < 5; i++) {
            store.addNode(NodeInput.of("meeting-" + i, sgId)
                .withProperties(Map.of("status", statuses[i])), "t1");
        }
        phase.run("t1", List.of("meeting"));

        SchemaField status = registry.schemaFor("meeting", "t1").get("status");
        assertThat(status).isNotNull();
        assertThat(status.enumValues()).containsExactlyInAnyOrder("active", "completed", "cancelled");
    }

    @Test
    void writesProvenanceFields() {
        for (int i = 0; i < 5; i++) {
            store.addNode(NodeInput.of("meeting-" + i, sgId)
                .withProperties(Map.of("date", "2026-01-01")), "t1");
        }
        phase.run("t1", List.of("meeting"));

        MindMapNode typeNode = store.search(MindMapQuery.of("t1", 100)
            .withType(SubgraphTypes.TYPE_SYSTEM)).stream()
            .filter(n -> n.name().equals("meeting")).findFirst().orElseThrow();
        assertThat(typeNode.property("schema.date.source")).hasValue("discovered");
        assertThat(typeNode.property("schema.date.first-seen-epoch")).isPresent();
    }
}
