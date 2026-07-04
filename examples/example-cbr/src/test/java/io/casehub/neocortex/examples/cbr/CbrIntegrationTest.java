package io.casehub.neocortex.examples.cbr;

import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.MemoryDomain;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(QdrantTestResource.class)
@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CbrIntegrationTest {

    @Inject CbrCaseMemoryStore store;

    @Test
    @Order(1)
    void seedAllDomains() {
        // Register schemas and store all seed data
        AmlInvestigationDemo.run(store);
        ClinicalAdverseEventDemo.run(store);
        DevtownPrReviewDemo.run(store);
        LifeContractorDemo.run(store);
        IotSituationDemo.run(store);
        QuarkmindBattleDemo.run(store);
    }

    @Test
    @Order(2)
    void denseVectorSearchRanksByProblemSimilarity() {
        // Query with problem text — Qdrant should rank by embedding similarity
        var query = CbrQuery.of("demo", new MemoryDomain("aml"),
                "aml-investigation", Map.of("transaction_pattern", "STRUCTURING"), 10)
            .withProblem("cash deposits split across branches to avoid reporting threshold");

        var results = store.retrieveSimilar(query, FeatureVectorCbrCase.class);
        assertThat(results).isNotEmpty();
        // With dense vector search, scores should be < 1.0 (real cosine similarity)
        assertThat(results.get(0).score()).isLessThan(1.0);
        assertThat(results.get(0).score()).isGreaterThan(0.0);
    }

    @Test
    @Order(2)
    void minSimilarityFiltersLowScores() {
        var query = CbrQuery.of("demo", new MemoryDomain("aml"),
                "aml-investigation", Map.of("transaction_pattern", "STRUCTURING"), 10)
            .withProblem("cash deposits split across branches")
            .withMinSimilarity(0.99);

        var results = store.retrieveSimilar(query, FeatureVectorCbrCase.class);
        // Very high threshold should filter most results
        assertThat(results).hasSizeLessThan(4);
    }

    @Test
    @Order(2)
    void planTraceRoundTripsThroughQdrant() {
        var query = CbrQuery.of("demo", new MemoryDomain("quarkmind"),
                "quarkmind-battle",
                Map.of("opponent_race", "ZERG", "detected_build", "ROACH_RUSH"), 10);

        var results = store.retrieveSimilar(query, PlanCbrCase.class);
        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(r -> {
            assertThat(r.cbrCase().planTrace()).isNotEmpty();
            assertThat(r.cbrCase().planTrace().get(0).bindingName()).isNotBlank();
        });
    }

    @Test
    @Order(2)
    void crossDomainIsolation() {
        // AML query should not return clinical cases
        var query = CbrQuery.of("demo", new MemoryDomain("aml"),
                "aml-investigation", Map.of("transaction_pattern", "STRUCTURING"), 100);

        var results = store.retrieveSimilar(query, FeatureVectorCbrCase.class);
        assertThat(results).allSatisfy(r ->
            assertThat(r.cbrCase().features().get("transaction_pattern")).isEqualTo("STRUCTURING"));
    }

    @Test
    @Order(3)
    void notBeforeFiltersOldCases() {
        // All seed cases were just stored — notBefore set to future should return nothing
        var query = CbrQuery.of("demo", new MemoryDomain("aml"),
                "aml-investigation", Map.of("transaction_pattern", "STRUCTURING"), 10)
            .withNotBefore(java.time.Instant.now().plusSeconds(3600));

        var results = store.retrieveSimilar(query, FeatureVectorCbrCase.class);
        assertThat(results).isEmpty();
    }
}
