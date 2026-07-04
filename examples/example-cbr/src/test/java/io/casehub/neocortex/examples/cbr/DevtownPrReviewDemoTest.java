package io.casehub.neocortex.examples.cbr;

import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.inmem.InMemoryCbrCaseMemoryStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("smoke")
class DevtownPrReviewDemoTest {

    @Test
    void refactorQueryReturnsMatchingCases() {
        var store = new InMemoryCbrCaseMemoryStore();
        var results = DevtownPrReviewDemo.run(store);

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(r -> {
            assertThat(r.scored().score()).isEqualTo(1.0);
            assertThat(r.scored().cbrCase()).isInstanceOf(FeatureVectorCbrCase.class);
            var c = (FeatureVectorCbrCase) r.scored().cbrCase();
            assertThat(c.problem()).isNotBlank();
            assertThat(c.solution()).isNotBlank();
            assertThat(c.features().get("change_type")).isEqualTo("REFACTOR");
        });
    }

    @Test
    void resultCountMatchesSeedData() {
        var store = new InMemoryCbrCaseMemoryStore();
        var results = DevtownPrReviewDemo.run(store);
        // 5 of 10 seed cases have change_type=REFACTOR
        assertThat(results).hasSize(5);
    }

    @Test
    void outcomesIncludeApprovedAndChangesRequested() {
        var store = new InMemoryCbrCaseMemoryStore();
        var results = DevtownPrReviewDemo.run(store);
        var outcomes = results.stream()
            .map(r -> ((FeatureVectorCbrCase) r.scored().cbrCase()).outcome())
            .toList();
        assertThat(outcomes).contains("APPROVED", "CHANGES_REQUESTED");
    }
}
