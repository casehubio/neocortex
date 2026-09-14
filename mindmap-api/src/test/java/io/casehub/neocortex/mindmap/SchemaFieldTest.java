package io.casehub.neocortex.mindmap;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class SchemaFieldTest {

    @Test
    void threeArgConstructor_setsDefaults() {
        var sf = new SchemaField("name", "string", true);
        assertThat(sf.collection()).isFalse();
        assertThat(sf.description()).isNull();
        assertThat(sf.enumValues()).isNull();
    }

    @Test
    void fullConstructor_setsAllFields() {
        var sf = new SchemaField("status", "string", false, false,
            "current status", List.of("active", "completed", "cancelled"));
        assertThat(sf.name()).isEqualTo("status");
        assertThat(sf.type()).isEqualTo("string");
        assertThat(sf.required()).isFalse();
        assertThat(sf.collection()).isFalse();
        assertThat(sf.description()).isEqualTo("current status");
        assertThat(sf.enumValues()).containsExactly("active", "completed", "cancelled");
    }

    @Test
    void collectionField_flagsCorrectly() {
        var sf = new SchemaField("attendees", "string", false, true, null, null);
        assertThat(sf.collection()).isTrue();
    }
}
