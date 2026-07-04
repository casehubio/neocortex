package io.casehub.neocortex.examples.cbr;

import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.inmem.InMemoryCbrCaseMemoryStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("smoke")
class IotSituationDemoTest {

    @Test
    void temperatureAnomalyKitchenQueryReturnsMatchingCases() {
        var store = new InMemoryCbrCaseMemoryStore();
        var results = IotSituationDemo.run(store);

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(r -> {
            assertThat(r.scored().score()).isEqualTo(1.0);
            assertThat(r.scored().cbrCase()).isInstanceOf(FeatureVectorCbrCase.class);
            var c = (FeatureVectorCbrCase) r.scored().cbrCase();
            assertThat(c.problem()).isNotBlank();
            assertThat(c.solution()).isNotBlank();
            assertThat(c.features().get("situation_type")).isEqualTo("TEMPERATURE_ANOMALY");
            assertThat(c.features().get("room_type")).isEqualTo("KITCHEN");
        });
    }

    @Test
    void resultCountMatchesSeedData() {
        var store = new InMemoryCbrCaseMemoryStore();
        var results = IotSituationDemo.run(store);
        // 5 of 10 seed cases have situation_type=TEMPERATURE_ANOMALY + room_type=KITCHEN
        assertThat(results).hasSize(5);
    }

    @Test
    void outcomesIncludeDismissedAndWorkItemCreated() {
        var store = new InMemoryCbrCaseMemoryStore();
        var results = IotSituationDemo.run(store);
        var outcomes = results.stream()
            .map(r -> ((FeatureVectorCbrCase) r.scored().cbrCase()).outcome())
            .toList();
        assertThat(outcomes).contains("OPERATOR_DISMISSED", "WORK_ITEM_CREATED");
    }
}
