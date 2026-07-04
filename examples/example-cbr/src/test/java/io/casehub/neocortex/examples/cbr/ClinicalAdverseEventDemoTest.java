package io.casehub.neocortex.examples.cbr;

import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.inmem.InMemoryCbrCaseMemoryStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("smoke")
class ClinicalAdverseEventDemoTest {

    @Test
    void hepatotoxicityQueryReturnsMatchingCases() {
        var store = new InMemoryCbrCaseMemoryStore();
        var results = ClinicalAdverseEventDemo.run(store);

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(r -> {
            assertThat(r.scored().score()).isEqualTo(1.0);
            assertThat(r.scored().cbrCase()).isInstanceOf(FeatureVectorCbrCase.class);
            var c = (FeatureVectorCbrCase) r.scored().cbrCase();
            assertThat(c.problem()).isNotBlank();
            assertThat(c.solution()).isNotBlank();
            assertThat(c.features().get("adverse_event_type")).isEqualTo("Hepatotoxicity");
            assertThat(c.features().get("trial_arm")).isEqualTo("TREATMENT");
        });
    }

    @Test
    void resultCountMatchesSeedData() {
        var store = new InMemoryCbrCaseMemoryStore();
        var results = ClinicalAdverseEventDemo.run(store);
        // 3 of 10 seed cases have adverse_event_type=Hepatotoxicity + trial_arm=TREATMENT
        assertThat(results).hasSize(3);
    }

    @Test
    void outcomesIncludeSafetyProtocol() {
        var store = new InMemoryCbrCaseMemoryStore();
        var results = ClinicalAdverseEventDemo.run(store);
        var outcomes = results.stream()
            .map(r -> ((FeatureVectorCbrCase) r.scored().cbrCase()).outcome())
            .toList();
        assertThat(outcomes).contains("SAFETY_PROTOCOL");
    }
}
