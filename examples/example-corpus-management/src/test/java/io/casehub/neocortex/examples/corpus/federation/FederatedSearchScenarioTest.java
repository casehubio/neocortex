package io.casehub.neocortex.examples.corpus.federation;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusLifecycle;
import io.casehub.neocortex.corpus.management.CorpusQuery;
import io.casehub.neocortex.corpus.management.FederationRef;
import io.casehub.neocortex.examples.corpus.CorpusTestHarness;
import io.casehub.neocortex.examples.corpus.domains.clinical.ClinicalCorpusTypes;
import io.casehub.neocortex.examples.corpus.domains.literature.LiteratureCorpusTypes;
import io.casehub.neocortex.rag.CorpusRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("smoke")
class FederatedSearchScenarioTest {

    private static final String TENANT = "federation-tenant";
    private CorpusTestHarness harness;
    private CorpusRef literatureCorpus;
    private CorpusRef clinicalCorpus;

    @BeforeEach
    void setUp() {
        harness = CorpusTestHarness.create();

        var litDesc = harness.catalogue().register(
            "evidence-base", LiteratureCorpusTypes.LITERATURE, TENANT,
            "vec-evidence-base", List.of(), List.of(), Map.of());
        literatureCorpus = litDesc.corpusRef();

        var clinDesc = harness.catalogue().register(
            "treatment-protocols", ClinicalCorpusTypes.CLINICAL_GUIDELINE, TENANT,
            "vec-treatment-protocols",
            List.of(new FederationRef(literatureCorpus, null, null, "default")),
            List.of(),
            Map.of());
        clinicalCorpus = clinDesc.corpusRef();

        harness.addEntry(literatureCorpus, new CorpusEntry("lit-f1",
            Map.of("title", "ACE Inhibitors in Hypertension: A Meta-Analysis",
                   "authors", List.of("Chen", "Liu"),
                   "year", 2022,
                   "abstract", "Meta-analysis of ACE inhibitor efficacy in hypertension treatment."),
            "ACE inhibitors reduce cardiovascular mortality in hypertensive patients by 15-20%.", null));
        harness.addEntry(literatureCorpus, new CorpusEntry("lit-f2",
            Map.of("title", "Metformin Outcomes in T2D: Long-Term Follow-Up",
                   "authors", List.of("Wilson", "Patel"),
                   "year", 2023,
                   "abstract", "Ten-year follow-up of metformin therapy outcomes."),
            "Long-term metformin therapy shows sustained HbA1c reduction with minimal adverse effects.", null));

        harness.addEntry(clinicalCorpus, new CorpusEntry("cg-f1",
            Map.of("title", "Hypertension — ACE Inhibitor Protocol",
                   "version", "2.0",
                   "effective_date", "2025-01-01T00:00:00Z",
                   "condition", "hypertension",
                   "evidence_grade", "A"),
            "ACE inhibitors are recommended as first-line for hypertension in patients under 55.", null));
        harness.addEntry(clinicalCorpus, new CorpusEntry("cg-f2",
            Map.of("title", "Diabetes — Insulin Titration",
                   "version", "1.0",
                   "effective_date", "2024-06-01T00:00:00Z",
                   "condition", "diabetes",
                   "evidence_grade", "B"),
            "Insulin titration protocol for patients not achieving target HbA1c with metformin alone.", null));
    }

    @Test
    void singleCorpusSearch() {
        var results = harness.search().search(literatureCorpus, CorpusQuery.of("ACE inhibitor"));
        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(r ->
            assertThat(r.sourceCorpus()).isEqualTo(literatureCorpus));
    }

    @Test
    void federatedSearchAcrossDomains() {
        var results = harness.federatedSearch().search(
            CorpusQuery.of("hypertension", 10), TENANT);
        assertThat(results.results()).isNotEmpty();

        var sourceCorpora = results.results().stream()
            .map(r -> r.sourceCorpus())
            .distinct()
            .toList();
        assertThat(sourceCorpora).containsExactlyInAnyOrder(literatureCorpus, clinicalCorpus);
    }

    @Test
    void federatedSearchWithDepthLimit() {
        var results = harness.federatedSearch().search(
            CorpusQuery.of("metformin", 10).withFederationDepth(0), TENANT);
        assertThat(results.results()).isNotEmpty();
    }

    @Test
    void archivedCorpusNotAStartingPoint() {
        harness.catalogue().updateLifecycle(literatureCorpus, CorpusLifecycle.ARCHIVED);

        assertThat(harness.catalogue().list(TENANT, CorpusLifecycle.ACTIVE))
            .extracting(d -> d.corpusRef())
            .doesNotContain(literatureCorpus)
            .contains(clinicalCorpus);
    }

    @Test
    void scopedFederatedSearch() {
        var results = harness.federatedSearch().search(
            CorpusQuery.of("ACE inhibitor", 10)
                .withCorpora(Set.of(clinicalCorpus)),
            TENANT);
        assertThat(results.results()).isNotEmpty();
        assertThat(results.results().stream().map(r -> r.sourceCorpus()).distinct().toList())
            .contains(clinicalCorpus);
    }

    @Test
    void searchReportTracksSearchedCorpora() {
        var results = harness.federatedSearch().search(
            CorpusQuery.of("hypertension", 10), TENANT);
        assertThat(results.report().searched()).isNotEmpty();
        assertThat(results.report().partial()).isFalse();
    }
}
