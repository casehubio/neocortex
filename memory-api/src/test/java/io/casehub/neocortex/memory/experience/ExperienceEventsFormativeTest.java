package io.casehub.neocortex.memory.experience;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExperienceEventsFormativeTest {

    @Test
    void formativeExperienceToMemoryInput() {
        var fe = new FormativeExperience(
            "agent1", "tenant1", null, null,
            Instant.EPOCH, "Caregiver responded warmly", 0.8,
            Map.of(), "attachment-secure",
            List.of("secure_attachment", "trust_building"), 3.0,
            "continuous", "infancy", 0.6, 0.3, 0.4);

        var input = ExperienceEvents.toMemoryInput(fe);

        assertThat(input.attributes())
            .containsEntry("event-type", "formative")
            .containsEntry("catalogue-entry-id", "attachment-secure")
            .containsEntry("situation-types", "secure_attachment,trust_building")
            .containsEntry("salience-multiplier", "3.0")
            .containsEntry("reinforcement-schedule", "continuous")
            .containsEntry("developmental-period", "infancy");
        assertThat(input.pleasure()).isEqualTo(0.6);
        assertThat(input.arousal()).isEqualTo(0.3);
        assertThat(input.dominance()).isEqualTo(0.4);
        assertThat(input.domain().name()).isEqualTo("experience");
    }

    @Test
    void formativeExperienceNullPadPassesThrough() {
        var fe = new FormativeExperience(
            "agent1", "tenant1", null, null,
            Instant.EPOCH, "desc", 0.8, Map.of(),
            "entry1", List.of("node1"), 1.0,
            null, null, null, null, null);

        var input = ExperienceEvents.toMemoryInput(fe);

        assertThat(input.pleasure()).isNull();
        assertThat(input.arousal()).isNull();
        assertThat(input.dominance()).isNull();
        assertThat(input.attributes())
            .doesNotContainKey("reinforcement-schedule")
            .doesNotContainKey("developmental-period");
    }
}
