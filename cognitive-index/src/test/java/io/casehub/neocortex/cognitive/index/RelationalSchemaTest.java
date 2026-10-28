package io.casehub.neocortex.cognitive.index;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class RelationalSchemaTest {

    @Test
    void roundTrip_preservesAllFields() {
        var schema = new RelationalSchema("peter-perfect", "obstacle", 0.1, 0.0, 0.2,
                "Predictably protective.");
        var props = schema.toProperties();
        var restored = RelationalSchema.fromProperties(props);
        assertThat(restored).isNotNull();
        assertThat(restored.targetId()).isEqualTo("peter-perfect");
        assertThat(restored.role()).isEqualTo("obstacle");
        assertThat(restored.trust()).isEqualTo(0.1);
        assertThat(restored.intimacy()).isEqualTo(0.0);
        assertThat(restored.utility()).isEqualTo(0.2);
        assertThat(restored.description()).isEqualTo("Predictably protective.");
    }

    @Test
    void fromProperties_noRelPrefix_returnsNull() {
        var props = Map.of("familiarity-score", "0.5");
        assertThat(RelationalSchema.fromProperties(props)).isNull();
    }

    @Test
    void fromProperties_partialProperties_usesDefaults() {
        var props = new HashMap<String, String>();
        props.put("rel.target-id", "stranger-1");
        props.put("rel.role", "ally");
        var schema = RelationalSchema.fromProperties(props);
        assertThat(schema).isNotNull();
        assertThat(schema.trust()).isEqualTo(0.0);
        assertThat(schema.intimacy()).isEqualTo(0.0);
        assertThat(schema.utility()).isEqualTo(0.0);
    }

    @Test
    void fromProperties_invalidNumeric_usesDefault() {
        var props = new HashMap<String, String>();
        props.put("rel.target-id", "target");
        props.put("rel.trust", "not-a-number");
        var schema = RelationalSchema.fromProperties(props);
        assertThat(schema).isNotNull();
        assertThat(schema.trust()).isEqualTo(0.0);
    }

    @Test
    void fromProperties_outOfRange_clamps() {
        var props = new HashMap<String, String>();
        props.put("rel.target-id", "target");
        props.put("rel.trust", "5.0");
        props.put("rel.intimacy", "-1.0");
        var schema = RelationalSchema.fromProperties(props);
        assertThat(schema).isNotNull();
        assertThat(schema.trust()).isEqualTo(1.0);
        assertThat(schema.intimacy()).isEqualTo(0.0);
    }

    @Test
    void trustRange_isNegativeOneToOne() {
        assertThatCode(() -> new RelationalSchema("t", "r", -1.0, 0.0, 0.0, null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new RelationalSchema("t", "r", -1.1, 0.0, 0.0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void propertiesUseRelPrefix() {
        var schema = new RelationalSchema("t", "ally", 0.5, 0.3, 0.7, null);
        var props = schema.toProperties();
        assertThat(props.keySet()).allMatch(k -> k.startsWith("rel."));
    }
}
