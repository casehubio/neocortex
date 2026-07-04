package io.casehub.neocortex.examples.cbr;

import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.inmem.InMemoryCbrCaseMemoryStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("smoke")
class LifeContractorDemoTest {

    @Test
    void plumbingHvacQueryReturnsMatchingCases() {
        var store = new InMemoryCbrCaseMemoryStore();
        var results = LifeContractorDemo.run(store);

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(r -> {
            assertThat(r.scored().score()).isEqualTo(1.0);
            assertThat(r.scored().cbrCase()).isInstanceOf(FeatureVectorCbrCase.class);
            var c = (FeatureVectorCbrCase) r.scored().cbrCase();
            assertThat(c.problem()).isNotBlank();
            assertThat(c.solution()).isNotBlank();
            assertThat(c.features().get("job_type")).isEqualTo("PLUMBING");
            assertThat(c.features().get("property_area")).isEqualTo("HVAC");
        });
    }

    @Test
    void resultCountMatchesSeedData() {
        var store = new InMemoryCbrCaseMemoryStore();
        var results = LifeContractorDemo.run(store);
        // 4 of 10 seed cases have job_type=PLUMBING + property_area=HVAC
        assertThat(results).hasSize(4);
    }

    @Test
    void outcomesIncludeCompletedAndDelayed() {
        var store = new InMemoryCbrCaseMemoryStore();
        var results = LifeContractorDemo.run(store);
        var outcomes = results.stream()
            .map(r -> ((FeatureVectorCbrCase) r.scored().cbrCase()).outcome())
            .toList();
        assertThat(outcomes).contains("COMPLETED_ON_TIME", "DELAYED");
    }
}
