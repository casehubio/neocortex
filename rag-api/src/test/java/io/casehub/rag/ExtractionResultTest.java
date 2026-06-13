package io.casehub.rag;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractionResultTest {

    @Test
    void validConstruction() {
        var result = new ExtractionResult("body text", Map.of("title", "Test"));
        assertThat(result.body()).isEqualTo("body text");
        assertThat(result.metadata()).containsEntry("title", "Test");
    }

    @Test
    void nullBodyThrows() {
        assertThatThrownBy(() -> new ExtractionResult(null, Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("body must not be null");
    }

    @Test
    void nullMetadataDefaultsToEmptyMap() {
        var result = new ExtractionResult("body", null);
        assertThat(result.metadata()).isEmpty();
    }

    @Test
    void metadataIsDefensivelyCopied() {
        var mutable = new HashMap<String, String>();
        mutable.put("key", "value");
        var result = new ExtractionResult("body", mutable);
        mutable.put("key2", "value2");
        assertThat(result.metadata()).containsOnlyKeys("key");
    }

    @Test
    void metadataIsUnmodifiable() {
        var result = new ExtractionResult("body", Map.of("key", "value"));
        assertThatThrownBy(() -> result.metadata().put("new", "value"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void emptyBodyIsAllowed() {
        var result = new ExtractionResult("", Map.of());
        assertThat(result.body()).isEmpty();
    }
}
