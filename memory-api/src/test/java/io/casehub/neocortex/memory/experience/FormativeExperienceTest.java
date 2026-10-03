package io.casehub.neocortex.memory.experience;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormativeExperienceTest {

    @Test
    void validFormativeExperience() {
        var fe = new FormativeExperience(
            "agent1", "tenant1", null, null,
            Instant.EPOCH, "A caregiver responded warmly", 0.8,
            Map.of(), "attachment-secure",
            List.of("secure_attachment"), 3.0,
            "continuous", "infancy", -0.3, 0.5, 0.2);

        assertThat(fe.agentId()).isEqualTo("agent1");
        assertThat(fe.catalogueEntryId()).isEqualTo("attachment-secure");
        assertThat(fe.situationTypes()).containsExactly("secure_attachment");
        assertThat(fe.salienceMultiplier()).isEqualTo(3.0);
        assertThat(fe.pleasure()).isEqualTo(-0.3);
        assertThat(fe).isInstanceOf(ExperienceEvent.class);
    }

    @Test
    void nullAgentIdThrows() {
        assertThatThrownBy(() -> new FormativeExperience(
            null, "tenant1", null, null,
            Instant.EPOCH, "desc", 0.8, Map.of(),
            "entry1", List.of("node1"), 1.0,
            null, null, null, null, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullCatalogueEntryIdThrows() {
        assertThatThrownBy(() -> new FormativeExperience(
            "agent1", "tenant1", null, null,
            Instant.EPOCH, "desc", 0.8, Map.of(),
            null, List.of("node1"), 1.0,
            null, null, null, null, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void emptySituationTypesThrows() {
        assertThatThrownBy(() -> new FormativeExperience(
            "agent1", "tenant1", null, null,
            Instant.EPOCH, "desc", 0.8, Map.of(),
            "entry1", List.of(), 1.0,
            null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void caseIdAndTurnIdCanBeNull() {
        var fe = new FormativeExperience(
            "agent1", "tenant1", null, null,
            Instant.EPOCH, "desc", 0.8, Map.of(),
            "entry1", List.of("node1"), 1.0,
            null, null, null, null, null);
        assertThat(fe.caseId()).isNull();
        assertThat(fe.turnId()).isNull();
    }
}
