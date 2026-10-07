package io.casehub.neocortex.memory.experience;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SubThoughtAttributeKeysTest {

    @Test
    void countConstant() {
        assertThat(SubThoughtAttributeKeys.COUNT).isEqualTo("sub-thought-count");
    }

    @Test
    void indexedKeyGeneration() {
        assertThat(SubThoughtAttributeKeys.type(0)).isEqualTo("sub-thought-0-type");
        assertThat(SubThoughtAttributeKeys.text(0)).isEqualTo("sub-thought-0-text");
        assertThat(SubThoughtAttributeKeys.entity(0)).isEqualTo("sub-thought-0-entity");
        assertThat(SubThoughtAttributeKeys.graduated(0)).isEqualTo("sub-thought-0-graduated");
    }

    @Test
    void indexedKeyGenerationHigherIndex() {
        assertThat(SubThoughtAttributeKeys.type(3)).isEqualTo("sub-thought-3-type");
        assertThat(SubThoughtAttributeKeys.text(12)).isEqualTo("sub-thought-12-text");
    }
}
