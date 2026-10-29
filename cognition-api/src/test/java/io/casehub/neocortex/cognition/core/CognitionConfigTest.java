package io.casehub.neocortex.cognition.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CognitionConfigTest {

    @Test
    void allDefaultsOtherModelToTrue() {
        assertThat(CognitionConfig.all().otherModelEnabled()).isTrue();
    }

    @Test
    void noneDefaultsOtherModelToFalse() {
        assertThat(CognitionConfig.none().otherModelEnabled()).isFalse();
    }

    @Test
    void withAllSubsystemsEnablesOtherModel() {
        assertThat(CognitionConfig.withAllSubsystems().otherModelEnabled()).isTrue();
    }

    @Test
    void withOtherModelToggles() {
        var config = CognitionConfig.all().with("otherModel", false);
        assertThat(config.otherModelEnabled()).isFalse();
        assertThat(config.moodEnabled()).isTrue();
    }

    @Test
    void withoutOtherModelDisables() {
        var config = CognitionConfig.all().without("otherModel");
        assertThat(config.otherModelEnabled()).isFalse();
    }
}
