package io.casehub.neocortex.examples.corpus.domains.clinical;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusLifecycle;
import io.casehub.neocortex.corpus.management.CorpusQuery;
import io.casehub.neocortex.examples.corpus.CorpusTestHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("smoke")
class ClinicalScenarioTest {

    private static final String TENANT = "clinical-tenant";
    private CorpusTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = CorpusTestHarness.create();
    }

    @Test
    void fullLifecycle() {
        var desc = harness.catalogue().register(
            "treatment-guidelines", ClinicalCorpusTypes.CLINICAL_GUIDELINE, TENANT,
            "vec-treatment-guidelines", List.of(), List.of(), Map.of());
        var corpus = desc.corpusRef();
        assertThat(desc.type().name()).isEqualTo("clinical-guideline");

        harness.addEntry(corpus, new CorpusEntry("cg-001",
            Map.of("title", "Hypertension Management — Initial Therapy",
                   "version", "3.0",
                   "effective_date", "2025-01-15T00:00:00Z",
                   "condition", "hypertension",
                   "evidence_grade", "A",
                   "supersedes", "cg-001-v2",
                   "contraindications", List.of("renal artery stenosis", "pregnancy")),
            "First-line therapy for uncomplicated hypertension is a thiazide diuretic or ACE inhibitor.", null));

        harness.addEntry(corpus, new CorpusEntry("cg-002",
            Map.of("title", "Type 2 Diabetes — Metformin Protocol",
                   "version", "2.1",
                   "effective_date", "2024-06-01T00:00:00Z",
                   "condition", "type-2-diabetes",
                   "evidence_grade", "A",
                   "review_date", "2026-06-01T00:00:00Z"),
            "Metformin is first-line pharmacotherapy for type 2 diabetes with lifestyle modification.", null));

        harness.addEntry(corpus, new CorpusEntry("cg-003",
            Map.of("title", "Acute Migraine — Triptan Administration",
                   "version", "1.0",
                   "effective_date", "2023-09-01T00:00:00Z",
                   "condition", "migraine",
                   "evidence_grade", "B",
                   "contraindications", List.of("coronary artery disease", "uncontrolled hypertension")),
            "Triptans should be administered within 2 hours of migraine onset for optimal efficacy.", null));

        harness.addEntry(corpus, new CorpusEntry("cg-004",
            Map.of("title", "Asthma — Step Therapy Protocol",
                   "version", "4.2",
                   "effective_date", "2025-03-01T00:00:00Z",
                   "condition", "asthma",
                   "evidence_grade", "A"),
            "Step therapy for persistent asthma begins with low-dose inhaled corticosteroids.", null));

        harness.addEntry(corpus, new CorpusEntry("cg-005",
            Map.of("title", "Anticoagulation — DVT Prophylaxis",
                   "version", "2.0",
                   "effective_date", "2024-11-01T00:00:00Z",
                   "condition", "dvt",
                   "evidence_grade", "B",
                   "contraindications", List.of("active bleeding", "thrombocytopenia")),
            "Prophylactic anticoagulation is recommended for post-surgical patients with moderate or high VTE risk.", null));

        var results = harness.search().search(corpus, CorpusQuery.of("therapy"));
        assertThat(results).isNotEmpty();

        var retrieved = harness.entryStore().get(corpus, "cg-001");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().stringField("version")).isEqualTo("3.0");
        assertThat(retrieved.get().stringListField("contraindications"))
            .containsExactly("renal artery stenosis", "pregnancy");

        assertThat(harness.catalogue().forType(ClinicalCorpusTypes.CLINICAL_GUIDELINE, TENANT)).hasSize(1);
        harness.catalogue().updateLifecycle(corpus, CorpusLifecycle.ARCHIVED);
        assertThat(harness.catalogue().list(TENANT, CorpusLifecycle.ACTIVE)).isEmpty();
        harness.catalogue().updateLifecycle(corpus, CorpusLifecycle.ACTIVE);
        assertThat(harness.catalogue().list(TENANT, CorpusLifecycle.ACTIVE)).hasSize(1);
    }
}
