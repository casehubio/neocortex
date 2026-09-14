package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.*;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class TypeRegistryProvenanceTest {

    private InMemoryMindMapStore store;
    private TypeRegistry registry;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        registry = new TypeRegistry(store);
    }

    @Test
    void registerType_withJavaClass_writesSourceJava() {
        registry.registerType("person", null, Personable.class, "t1");
        MindMapNode typeNode = findTypeNode("person", "t1");
        assertThat(typeNode.property("schema.role.source")).hasValue("java");
    }

    @Test
    void schemaFor_readsCollectionAndDescriptionFields() {
        registry.registerType("meeting", null, "t1");
        MindMapNode typeNode = findTypeNode("meeting", "t1");
        store.updateNode(typeNode.id(), new NodeUpdate(
            null, null, null, null, null, null, null, null,
            null, null, null,
            Map.of(
                "schema.attendees.type", "string",
                "schema.attendees.required", "false",
                "schema.attendees.collection", "true",
                "schema.attendees.description", "people attending",
                "schema.attendees.source", "discovered"
            ), null), "t1");

        Map<String, SchemaField> schema = registry.schemaFor("meeting", "t1");
        SchemaField attendees = schema.get("attendees");
        assertThat(attendees).isNotNull();
        assertThat(attendees.collection()).isTrue();
        assertThat(attendees.description()).isEqualTo("people attending");
        assertThat(attendees.type()).isEqualTo("string");
    }

    @Test
    void schemaFor_javaAndDiscovered_mergeWithJavaPrecedence() {
        registry.registerType("person", null, Personable.class, "t1");
        MindMapNode typeNode = findTypeNode("person", "t1");
        store.updateNode(typeNode.id(), new NodeUpdate(
            null, null, null, null, null, null, null, null,
            null, null, null,
            Map.of(
                "schema.department.type", "string",
                "schema.department.source", "discovered",
                "schema.department.description", "org department"
            ), null), "t1");

        Map<String, SchemaField> schema = registry.schemaFor("person", "t1");
        assertThat(schema).containsKey("role");
        assertThat(schema).containsKey("department");
        assertThat(schema.get("department").description()).isEqualTo("org department");
    }

    private MindMapNode findTypeNode(String typeName, String tenantId) {
        return store.search(MindMapQuery.of(tenantId, 100)
            .withType(SubgraphTypes.TYPE_SYSTEM)).stream()
            .filter(n -> n.name().equals(typeName))
            .findFirst().orElseThrow();
    }
}
