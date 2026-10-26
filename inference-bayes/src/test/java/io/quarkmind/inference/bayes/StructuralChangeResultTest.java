package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StructuralChangeResultTest {

    @Test
    void recordFieldsAccessible() {
        var result = new StructuralChangeResult(
                StructuralChangeResult.ChangeType.REBUILD,
                Set.of("A", "B"),
                2, 1, 12345L);

        assertThat(result.changeType()).isEqualTo(StructuralChangeResult.ChangeType.REBUILD);
        assertThat(result.affectedVariables()).containsExactlyInAnyOrder("A", "B");
        assertThat(result.evidenceRestored()).isEqualTo(2);
        assertThat(result.evidenceLost()).isEqualTo(1);
        assertThat(result.rebuildNanos()).isEqualTo(12345L);
    }
}
