package io.casehub.neocortex.memory.seeding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PadDeriverTest {

    @Test
    void threatNodeNegativePleasure() {
        var pad = PadDeriver.derive("rejection", 0.8);
        assertThat(pad.pleasure()).isNegative();
        assertThat(pad.arousal()).isPositive();
    }

    @Test
    void secureAttachmentPositivePleasure() {
        var pad = PadDeriver.derive("secure_attachment", 0.7);
        assertThat(pad.pleasure()).isPositive();
    }

    @Test
    void achievementHighDominance() {
        var pad = PadDeriver.derive("mastery_experience", 0.9);
        assertThat(pad.dominance()).isPositive();
        assertThat(pad.pleasure()).isPositive();
    }

    @Test
    void unknownNodeNeutral() {
        var pad = PadDeriver.derive("completely_unknown_node", 0.5);
        assertThat(pad.pleasure()).isEqualTo(0.0);
    }

    @Test
    void inconsistentCareIsRelationshipCategory() {
        var pad = PadDeriver.derive("inconsistent_care", 0.7);
        assertThat(pad.pleasure()).isPositive();
    }
}
