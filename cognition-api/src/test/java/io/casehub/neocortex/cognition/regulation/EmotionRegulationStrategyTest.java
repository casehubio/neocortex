package io.casehub.neocortex.cognition.regulation;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EmotionRegulationStrategyTest {

    @Test
    void socialStrategiesExist() {
        assertThat(EmotionRegulationStrategy.valueOf("SOCIAL_SUPPORT_SEEKING")).isNotNull();
        assertThat(EmotionRegulationStrategy.valueOf("SOCIAL_SHARING")).isNotNull();
        assertThat(EmotionRegulationStrategy.valueOf("SOCIAL_WITHDRAWAL")).isNotNull();
        assertThat(EmotionRegulationStrategy.valueOf("CO_REGULATION")).isNotNull();
        assertThat(EmotionRegulationStrategy.valueOf("DELIBERATE_PROVOCATION")).isNotNull();
    }

    @Test
    void tenStrategiesTotal() {
        assertThat(EmotionRegulationStrategy.values()).hasSize(10);
    }
}
