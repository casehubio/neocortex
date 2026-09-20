package io.casehub.neocortex.examples.cbr;

import io.casehub.neocortex.memory.cbr.CbrFeatureRecord;
import io.casehub.neocortex.memory.cbr.inmem.InMemoryCbrRecordStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.casehub.neocortex.memory.cbr.FeatureValue.*;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("smoke")
class AmlInvestigationDemoTest {

    @Test
    void structuringQueryReturnsMatchingCases() {
        var store = new InMemoryCbrRecordStore();
        var results = AmlInvestigationDemo.run(store);

        assertThat(results).isNotEmpty();
        // With graded similarity, matching cases score highest (1.0), non-matching cases score lower
        // Check that the top 4 results are the matching cases with score 1.0
        var topResults = results.stream().limit(4).toList();
        assertThat(topResults).allSatisfy(r -> {
            assertThat(r.scored().score()).isEqualTo(1.0);
            assertThat(r.scored().cbrRecord()).isInstanceOf(CbrFeatureRecord.class);
            var c = (CbrFeatureRecord) r.scored().cbrRecord();
            assertThat(c.problem()).isNotBlank();
            assertThat(c.solution()).isNotBlank();
            assertThat(c.features().get("transaction_pattern")).isEqualTo(string("STRUCTURING"));
        });
    }

    @Test
    void resultCountMatchesSeedData() {
        var store = new InMemoryCbrRecordStore();
        var results = AmlInvestigationDemo.run(store);
        // With graded similarity, all cases are returned (filtered by identity: tenant, domain, caseType)
        // The query returns all 10 seed cases, with matching cases scoring highest
        assertThat(results).hasSize(10);
        // Verify that 4 cases have perfect match scores (transaction_pattern=STRUCTURING)
        var perfectMatches = results.stream().filter(r -> r.scored().score() == 1.0).toList();
        assertThat(perfectMatches).hasSize(4);
    }

    @Test
    void outcomesIncludeSarFiledAndCleared() {
        var store = new InMemoryCbrRecordStore();
        var results = AmlInvestigationDemo.run(store);
        var outcomes = results.stream()
            .map(r -> ((CbrFeatureRecord) r.scored().cbrRecord()).outcome())
            .toList();
        assertThat(outcomes).contains("SAR_FILED", "CLEARED");
    }
}
