package io.casehub.neocortex.memory.mood;

import io.casehub.neocortex.cognitive.AffectType;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.MemoryInput;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AffectEventsTest {

    @Test
    void domain_isAffect() {
        assertThat(AffectEvents.DOMAIN).isEqualTo(new MemoryDomain("affect"));
    }

    @Test
    void toMemoryInput_setsEntityIdToNodeId() {
        MemoryInput input = AffectEvents.toMemoryInput("node-42", "tenant-1", 0.5, -0.3, 0.7);
        assertThat(input.entityId()).isEqualTo("node-42");
        assertThat(input.tenantId()).isEqualTo("tenant-1");
    }

    @Test
    void toMemoryInput_setsDomainToAffect() {
        MemoryInput input = AffectEvents.toMemoryInput("node-1", "t1", 0.0, 0.0, 0.0);
        assertThat(input.domain()).isEqualTo(AffectEvents.DOMAIN);
    }

    @Test
    void toMemoryInput_carriesPadAsTypedFields() {
        MemoryInput input = AffectEvents.toMemoryInput("node-1", "t1", 0.5, -0.3, 0.7);
        assertThat(input.pleasure()).isEqualTo(0.5);
        assertThat(input.arousal()).isEqualTo(-0.3);
        assertThat(input.dominance()).isEqualTo(0.7);
    }

    @Test
    void toMemoryInput_hasNonBlankText() {
        MemoryInput input = AffectEvents.toMemoryInput("node-1", "t1", 0.0, 0.0, 0.0);
        assertThat(input.text()).isNotBlank();
    }

    @Test
    void toMemoryInput_nullConfidence() {
        MemoryInput input = AffectEvents.toMemoryInput("node-1", "t1", 0.5, 0.5, 0.5);
        assertThat(input.confidence()).isNull();
    }

    @Test
    void toMemoryInput_withInherentType_setsAttribute() {
        MemoryInput input = AffectEvents.toMemoryInput("node-1", "t1", 0.5, -0.3, 0.7, AffectType.INHERENT);
        assertThat(input.attributes()).containsEntry("affect-type", "inherent");
    }

    @Test
    void toMemoryInput_withAnticipatoryType_setsAttribute() {
        MemoryInput input = AffectEvents.toMemoryInput("node-1", "t1", 0.5, -0.3, 0.7, AffectType.ANTICIPATORY);
        assertThat(input.attributes()).containsEntry("affect-type", "anticipatory");
    }

    @Test
    void toMemoryInput_defaultOverload_setsInherent() {
        MemoryInput input = AffectEvents.toMemoryInput("node-1", "t1", 0.5, -0.3, 0.7);
        assertThat(input.attributes()).containsEntry("affect-type", "inherent");
    }
}
