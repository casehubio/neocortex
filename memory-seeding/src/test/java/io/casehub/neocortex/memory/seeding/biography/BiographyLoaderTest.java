package io.casehub.neocortex.memory.seeding.biography;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BiographyLoaderTest {

    @Test
    void loadsPlaceEntries() {
        var loader = new BiographyLoader();
        var profile = loader.loadAll(
            Path.of("src/test/resources/biography-test"), "agent-1", "tenant-1");
        assertThat(profile.places()).hasSize(1);
        assertThat(profile.places().getFirst().name()).isEqualTo("La Casa Azul");
        assertThat(profile.places().getFirst().pad().pleasure()).isEqualTo(0.6);
        assertThat(profile.places().getFirst().sourceRef()).isEqualTo("02-childhood.md#casa-azul");
    }

    @Test
    void rejectsDuplicateIds(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("a.yaml"),
            "type: place\nentries:\n  - id: dup\n    name: A\n");
        Files.writeString(dir.resolve("b.yaml"),
            "type: place\nentries:\n  - id: dup\n    name: B\n");

        var loader = new BiographyLoader();
        assertThatThrownBy(() -> loader.loadAll(dir, "a1", "t1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate biography entry ID: dup");
    }

    @Test
    void loadsEmptyDirectory(@TempDir Path dir) throws IOException {
        var loader = new BiographyLoader();
        var profile = loader.loadAll(dir, "agent-1", "tenant-1");
        assertThat(profile.places()).isEmpty();
        assertThat(profile.goals()).isEmpty();
    }
}
