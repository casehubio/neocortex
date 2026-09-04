package io.casehub.neocortex.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectTest {

    @Test
    void of_normalizesTypeToLowercase() {
        var s = Subject.of("Person", "alice");
        assertThat(s.type()).isEqualTo("person");
        assertThat(s.id()).isEqualTo("alice");
    }

    @Test
    void of_stripsWhitespace() {
        var s = Subject.of(" person ", " alice ");
        assertThat(s.type()).isEqualTo("person");
        assertThat(s.id()).isEqualTo("alice");
    }

    @Test
    void nullType_throws() {
        assertThatThrownBy(() -> Subject.of(null, "alice"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void blankType_throws() {
        assertThatThrownBy(() -> Subject.of("", "alice"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullId_throws() {
        assertThatThrownBy(() -> Subject.of("person", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void blankId_throws() {
        assertThatThrownBy(() -> Subject.of("person", ""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equality_sameTypeAndId() {
        assertThat(Subject.of("person", "alice"))
            .isEqualTo(Subject.of("Person", "alice"));
    }

    @Test
    void equality_differentType() {
        assertThat(Subject.of("person", "alice"))
            .isNotEqualTo(Subject.of("agent", "alice"));
    }

    @Test
    void equality_differentId() {
        assertThat(Subject.of("person", "alice"))
            .isNotEqualTo(Subject.of("person", "bob"));
    }

    @Test
    void toString_readable() {
        assertThat(Subject.of("person", "alice").toString())
            .contains("person")
            .contains("alice");
    }
}
