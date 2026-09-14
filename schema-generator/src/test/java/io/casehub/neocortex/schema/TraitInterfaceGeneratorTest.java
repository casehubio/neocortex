package io.casehub.neocortex.schema;

import io.casehub.neocortex.mindmap.SchemaField;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class TraitInterfaceGeneratorTest {

    @Test
    void generate_simpleType_producesCorrectInterface() {
        Map<String, SchemaField> schema = new LinkedHashMap<>();
        schema.put("date", new SchemaField("date", "string", true));
        schema.put("agenda", new SchemaField("agenda", "string", false));

        String source = TraitInterfaceGenerator.generate(
            "meeting", schema, "io.casehub.neocortex.mindmap.intelligence");

        assertThat(source).contains("package io.casehub.neocortex.mindmap.intelligence;");
        assertThat(source).contains("import java.util.Optional;");
        assertThat(source).contains("public interface Meetinglike {");
        assertThat(source).contains("Optional<String> date();");
        assertThat(source).contains("Optional<String> agenda();");
    }

    @Test
    void generate_hyphenatedTypeName_convertsToPascalCase() {
        Map<String, SchemaField> schema = new LinkedHashMap<>();
        schema.put("title", new SchemaField("title", "string", false));

        String source = TraitInterfaceGenerator.generate(
            "research-report", schema, "io.casehub.example");

        assertThat(source).contains("public interface ResearchReportlike {");
    }

    @Test
    void generate_emptySchema_producesEmptyInterface() {
        String source = TraitInterfaceGenerator.generate(
            "empty", Map.of(), "io.casehub.example");

        assertThat(source).contains("public interface Emptylike {");
        assertThat(source).doesNotContain("Optional<String>");
    }

    @Test
    void toInterfaceName_convertsCorrectly() {
        assertThat(TraitInterfaceGenerator.toInterfaceName("meeting")).isEqualTo("Meetinglike");
        assertThat(TraitInterfaceGenerator.toInterfaceName("research-report")).isEqualTo("ResearchReportlike");
        assertThat(TraitInterfaceGenerator.toInterfaceName("org-unit")).isEqualTo("OrgUnitlike");
    }
}
