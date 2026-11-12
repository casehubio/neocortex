package io.casehub.neocortex.corpus;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersionInfoTest {

    @Test
    void validConstruction() {
        var now = Instant.now();
        var info = new VersionInfo(5, now, "corpus_v5.zip");
        assertThat(info.version()).isEqualTo(5);
        assertThat(info.timestamp()).isEqualTo(now);
        assertThat(info.source()).isEqualTo("corpus_v5.zip");
    }

    @Test
    void versionMustBeAtLeastOne() {
        assertThatThrownBy(() -> new VersionInfo(0, Instant.now(), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("version must be >= 1");

        assertThatThrownBy(() -> new VersionInfo(-1, Instant.now(), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("version must be >= 1");
    }

    @Test
    void nullTimestampThrows() {
        assertThatThrownBy(() -> new VersionInfo(1, null, "file.zip"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("timestamp must not be null");
    }

    @Test
    void nullZipFileAllowed() {
        var info = new VersionInfo(1, Instant.now(), null);
        assertThat(info.source()).isNull();
    }

    @Test
    void shouldCarryAuthor() {
        var info = new VersionInfo(1, Instant.now(), "abc123", "Alice");
        assertThat(info.author()).isEqualTo("Alice");
    }

    @Test
    void shouldDefaultAuthorToNull() {
        var info = new VersionInfo(1, Instant.now(), "abc123");
        assertThat(info.author()).isNull();
    }


}
