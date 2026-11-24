package io.casehub.neocortex.cognition.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CognitionConfigTest {

    @Test
    void allEnablesEverySubsystem() {
        var config = CognitionConfig.all();
        for (var s : CognitionConfig.Subsystem.values()) {
            assertThat(config.isEnabled(s))
                    .as("all() should enable %s", s)
                    .isTrue();
        }
    }

    @Test
    void noneDisablesEverySubsystem() {
        var config = CognitionConfig.none();
        for (var s : CognitionConfig.Subsystem.values()) {
            assertThat(config.isEnabled(s))
                    .as("none() should disable %s", s)
                    .isFalse();
        }
    }

    @Test
    void allAndWithAllSubsystemsAreIdentical() {
        assertThat(CognitionConfig.all()).isEqualTo(CognitionConfig.withAllSubsystems());
    }

    @Test
    void withSubsystemAdds() {
        var config = CognitionConfig.none().with(CognitionConfig.Subsystem.MOOD);
        assertThat(config.isEnabled(CognitionConfig.Subsystem.MOOD)).isTrue();
        assertThat(config.isEnabled(CognitionConfig.Subsystem.DRIVES)).isFalse();
    }

    @Test
    void withoutSubsystemRemoves() {
        var config = CognitionConfig.all().without(CognitionConfig.Subsystem.MOOD);
        assertThat(config.isEnabled(CognitionConfig.Subsystem.MOOD)).isFalse();
        assertThat(config.isEnabled(CognitionConfig.Subsystem.DRIVES)).isTrue();
    }

    @Test
    void stringBasedWithToggles() {
        var config = CognitionConfig.all().with("otherModel", false);
        assertThat(config.otherModelEnabled()).isFalse();
        assertThat(config.moodEnabled()).isTrue();
    }

    @Test
    void stringBasedWithoutDisables() {
        var config = CognitionConfig.all().without("otherModel");
        assertThat(config.otherModelEnabled()).isFalse();
    }

    @Test
    void stringBasedWithEnables() {
        var config = CognitionConfig.none().with("regulation", true);
        assertThat(config.regulationEnabled()).isTrue();
    }

    @Test
    void withoutStringVarargs() {
        var config = CognitionConfig.all().without("regulation", "mood");
        assertThat(config.regulationEnabled()).isFalse();
        assertThat(config.moodEnabled()).isFalse();
        assertThat(config.drivesEnabled()).isTrue();
    }

    @Test
    void withDirectivesEnablesDirectivePrompts() {
        var config = CognitionConfig.none().withDirectives();
        assertThat(config.directivePrompts()).isTrue();
    }

    @Test
    void unknownSubsystemThrows() {
        assertThatThrownBy(() -> CognitionConfig.all().with("bogus", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bogus");
    }

    @Test
    void equalsAndHashCode() {
        assertThat(CognitionConfig.all()).isEqualTo(CognitionConfig.all());
        assertThat(CognitionConfig.all()).isNotEqualTo(CognitionConfig.none());
        assertThat(CognitionConfig.all().hashCode()).isEqualTo(CognitionConfig.all().hashCode());
    }

    @Test
    void backwardCompatAccessors() {
        var config = CognitionConfig.all();
        assertThat(config.moodEnabled()).isTrue();
        assertThat(config.drivesEnabled()).isTrue();
        assertThat(config.entityKnowledgeEnabled()).isTrue();
        assertThat(config.regulationEnabled()).isTrue();
        assertThat(config.dissonanceEnabled()).isTrue();

        var none = CognitionConfig.none();
        assertThat(none.moodEnabled()).isFalse();
        assertThat(none.regulationEnabled()).isFalse();
    }

    @Test
    void enabledSubsystemsReturnsUnmodifiableSet() {
        var config = CognitionConfig.all();
        var set = config.enabledSubsystems();
        assertThat(set).hasSize(CognitionConfig.Subsystem.values().length);
        assertThatThrownBy(() -> set.add(CognitionConfig.Subsystem.MOOD))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
