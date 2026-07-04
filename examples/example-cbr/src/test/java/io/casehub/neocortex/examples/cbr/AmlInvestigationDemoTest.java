package io.casehub.neocortex.examples.cbr;

import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.inmem.InMemoryCbrCaseMemoryStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("smoke")
class AmlInvestigationDemoTest {

    @Test
    void structuringQueryReturnsMatchingCases() {
        var store = new InMemoryCbrCaseMemoryStore();
        var results = AmlInvestigationDemo.run(store);

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(r -> {
            assertThat(r.scored().score()).isEqualTo(1.0);
            assertThat(r.scored().cbrCase()).isInstanceOf(FeatureVectorCbrCase.class);
            var c = (FeatureVectorCbrCase) r.scored().cbrCase();
            assertThat(c.problem()).isNotBlank();
            assertThat(c.solution()).isNotBlank();
            assertThat(c.features().get("transaction_pattern")).isEqualTo("STRUCTURING");
        });
    }

    @Test
    void resultCountMatchesSeedData() {
        var store = new InMemoryCbrCaseMemoryStore();
        var results = AmlInvestigationDemo.run(store);
        // 4 of 10 seed cases have transaction_pattern=STRUCTURING
        assertThat(results).hasSize(4);
    }

    @Test
    void outcomesIncludeSarFiledAndCleared() {
        var store = new InMemoryCbrCaseMemoryStore();
        var results = AmlInvestigationDemo.run(store);
        var outcomes = results.stream()
            .map(r -> ((FeatureVectorCbrCase) r.scored().cbrCase()).outcome())
            .toList();
        assertThat(outcomes).contains("SAR_FILED", "CLEARED");
    }
}
