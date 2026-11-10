package io.casehub.neocortex.corpus.management;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredChunkTest {

    @Test
    void structuredChunkCreation() {
        var chunk = new StructuredChunk("some text", Map.of("page", "1", "panel", "left"));
        assertThat(chunk.text()).isEqualTo("some text");
        assertThat(chunk.metadata()).containsEntry("page", "1");
        assertThat(chunk.metadata()).containsEntry("panel", "left");
    }

    @Test
    void structuredChunkRejectsNullText() {
        assertThatThrownBy(() -> new StructuredChunk(null, Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void structuredChunkDefaultsNullMetadata() {
        var chunk = new StructuredChunk("text", null);
        assertThat(chunk.metadata()).isEmpty();
    }
}
