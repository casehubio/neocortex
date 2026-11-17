package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.CognitiveCapacityConfig;
import io.casehub.neocortex.cognitive.CognitiveResourcePoolConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveResourceDefaultsTest {

    @Test
    void empty_allFieldsNull() {
        var empty = CognitiveResourceDefaults.empty();
        assertThat(empty.capacityConfig()).isNull();
        assertThat(empty.cognitiveResourcePoolConfig()).isNull();
        assertThat(empty.searchBudgetConfig()).isNull();
        assertThat(empty.recurringThoughtConfig()).isNull();
        assertThat(empty.habituationConfig()).isNull();
    }

    @Test
    void mergeWith_explicitWins() {
        var explicitCapacity = new CognitiveCapacityConfig(0.9, null, null);
        var derivedCapacity = new CognitiveCapacityConfig(0.5, 0.6, 0.7);
        var derivedPool = CognitiveResourcePoolConfig.defaults();

        var explicit = new CognitiveResourceDefaults(explicitCapacity, null, null, null, null);
        var derived = new CognitiveResourceDefaults(derivedCapacity, derivedPool, null, null, null);

        var merged = explicit.mergeWith(derived);
        assertThat(merged.capacityConfig().relationalComplexity()).isEqualTo(0.9);
        assertThat(merged.capacityConfig().associativeReach()).isEqualTo(0.6);
        assertThat(merged.capacityConfig().workingMemorySpan()).isEqualTo(0.7);
        assertThat(merged.cognitiveResourcePoolConfig()).isSameAs(derivedPool);
    }

    @Test
    void mergeWith_capacityBothNull() {
        var merged = CognitiveResourceDefaults.empty().mergeWith(CognitiveResourceDefaults.empty());
        assertThat(merged.capacityConfig()).isNull();
    }

    @Test
    void mergeWith_derivedNull_returnsThis() {
        var explicit = new CognitiveResourceDefaults(CognitiveCapacityConfig.defaults(), null, null, null, null);
        var merged = explicit.mergeWith(null);
        assertThat(merged).isSameAs(explicit);
    }
}
