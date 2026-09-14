package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.SchemaField;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class MindMapExtractorSchemaTest {

    @Test
    void buildSchemaHint_formatsSchemaAsProfileData() {
        Map<String, SchemaField> schema = new LinkedHashMap<>();
        schema.put("date", new SchemaField("date", "string", true));
        schema.put("attendees", new SchemaField("attendees", "string", false, true, null, null));

        String hint = MindMapExtractor.buildSchemaHint(Map.of("meeting", schema));
        assertThat(hint).contains("Known type schemas:");
        assertThat(hint).contains("meeting:");
        assertThat(hint).contains("date: string (required)");
        assertThat(hint).contains("attendees: string (collection)");
        assertThat(hint).contains("Extract all observed properties");
    }

    @Test
    void buildSchemaHint_emptySchemas_returnsEmpty() {
        String hint = MindMapExtractor.buildSchemaHint(Map.of());
        assertThat(hint).isEmpty();
    }

    @Test
    void buildSchemaHint_includesEnumValues() {
        Map<String, SchemaField> schema = new LinkedHashMap<>();
        schema.put("status", new SchemaField("status", "string", false, false,
            null, List.of("active", "completed", "cancelled")));

        String hint = MindMapExtractor.buildSchemaHint(Map.of("task", schema));
        assertThat(hint).contains("status: string [active, completed, cancelled]");
    }

    @Test
    void buildSchemaHint_includesDescription() {
        Map<String, SchemaField> schema = new LinkedHashMap<>();
        schema.put("agenda", new SchemaField("agenda", "string", false, false,
            "planned discussion topics", null));

        String hint = MindMapExtractor.buildSchemaHint(Map.of("meeting", schema));
        assertThat(hint).contains("agenda: string — planned discussion topics");
    }
}
