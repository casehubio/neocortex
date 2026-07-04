package io.casehub.neocortex.examples.cbr;

import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.inmem.InMemoryCbrCaseMemoryStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("smoke")
class QuarkmindBattleDemoTest {

    @Test
    void zergRoachRushQueryReturnsMatchingGames() {
        var store = new InMemoryCbrCaseMemoryStore();
        var results = QuarkmindBattleDemo.run(store);

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(r -> {
            assertThat(r.scored().score()).isEqualTo(1.0);
            assertThat(r.scored().cbrCase()).isInstanceOf(PlanCbrCase.class);
            var c = r.scored().cbrCase();
            assertThat(c.features().get("opponent_race")).isEqualTo("ZERG");
            assertThat(c.features().get("detected_build")).isEqualTo("ROACH_RUSH");
        });
    }

    @Test
    void resultCountMatchesSeedData() {
        var store = new InMemoryCbrCaseMemoryStore();
        var results = QuarkmindBattleDemo.run(store);
        // 5 of 10 seed cases have opponent_race=ZERG + detected_build=ROACH_RUSH
        assertThat(results).hasSize(5);
    }

    @Test
    void planTracesArePreserved() {
        var store = new InMemoryCbrCaseMemoryStore();
        var results = QuarkmindBattleDemo.run(store);
        assertThat(results).allSatisfy(r -> {
            var c = r.scored().cbrCase();
            assertThat(c.planTrace()).isNotEmpty();
            assertThat(c.planTrace()).allSatisfy(t -> {
                assertThat(t.bindingName()).isNotBlank();
                assertThat(t.capabilityName()).isNotBlank();
                assertThat(t.priority()).isGreaterThanOrEqualTo(0);
            });
        });
    }

    @Test
    void planAnalysisShowsScoutCorrelation() {
        var store = new InMemoryCbrCaseMemoryStore();
        var results = QuarkmindBattleDemo.run(store);
        // Games with "scout" binding should correlate with wins
        long winsWithScout = results.stream()
            .filter(r -> "WIN".equals(r.scored().cbrCase().outcome()))
            .filter(r -> r.scored().cbrCase().planTrace().stream()
                .anyMatch(t -> "scout".equals(t.bindingName())))
            .count();
        assertThat(winsWithScout).isGreaterThanOrEqualTo(3);
    }
}
