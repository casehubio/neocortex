package io.casehub.neocortex.examples.corpus.domains.literature;

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
class LiteratureScenarioTest {

    private static final String TENANT = "literature-tenant";
    private CorpusTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = CorpusTestHarness.create();
    }

    @Test
    void fullLifecycle() {
        var desc = harness.catalogue().register(
            "ai-papers", LiteratureCorpusTypes.LITERATURE, TENANT,
            "vec-ai-papers", List.of(), List.of(), Map.of());
        var corpus = desc.corpusRef();
        assertThat(desc.type().name()).isEqualTo("academic-literature");

        harness.addEntry(corpus, new CorpusEntry("lit-001",
            Map.of("title", "Case-Based Reasoning: Foundational Issues",
                   "authors", List.of("Aamodt", "Plaza"),
                   "year", 1994,
                   "abstract", "A survey of case-based reasoning approaches and architectures.",
                   "keywords", List.of("CBR", "knowledge representation", "AI")),
            "Case-based reasoning is a problem-solving paradigm that solves new problems by adapting solutions from similar past cases.", null));

        harness.addEntry(corpus, new CorpusEntry("lit-002",
            Map.of("title", "Retrieval-Augmented Generation for Knowledge-Intensive Tasks",
                   "authors", List.of("Lewis", "Perez", "Piktus", "Petroni", "Karpukhin"),
                   "year", 2020,
                   "abstract", "RAG combines retrieval with generation for knowledge-intensive NLP.",
                   "doi", "10.48550/arXiv.2005.11401",
                   "citations", List.of("lit-001")),
            "We explore retrieval-augmented generation models that combine parametric and non-parametric memories.", null));

        harness.addEntry(corpus, new CorpusEntry("lit-003",
            Map.of("title", "Knowledge Graphs: Foundations and Applications",
                   "authors", List.of("Hogan", "Blomqvist", "Gutierrez"),
                   "year", 2021,
                   "abstract", "A comprehensive survey of knowledge graph construction and querying.",
                   "journal", "ACM Computing Surveys",
                   "methodology", "systematic literature review"),
            "Knowledge graphs have become a cornerstone for organizing structured knowledge.", null));

        harness.addEntry(corpus, new CorpusEntry("lit-004",
            Map.of("title", "SOAR: A General Cognitive Architecture",
                   "authors", List.of("Laird", "Newell", "Rosenbloom"),
                   "year", 1987,
                   "abstract", "SOAR integrates problem solving, learning, and memory.",
                   "keywords", List.of("cognitive architecture", "production systems")),
            "SOAR is a general cognitive architecture for developing intelligent systems.", null));

        harness.addEntry(corpus, new CorpusEntry("lit-005",
            Map.of("title", "Dense Passage Retrieval for Open-Domain QA",
                   "authors", List.of("Karpukhin", "Oguz", "Min"),
                   "year", 2020,
                   "abstract", "DPR uses dense representations for efficient passage retrieval.",
                   "citations", List.of("lit-001", "lit-002")),
            "Dense passage retrieval significantly outperforms traditional sparse retrieval for open-domain QA.", null));

        var results = harness.search().search(corpus, CorpusQuery.of("retrieval"));
        assertThat(results).isNotEmpty();
        assertThat(results).extracting(r -> r.entry().id())
            .contains("lit-002", "lit-005");

        var retrieved = harness.entryStore().get(corpus, "lit-002");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().intField("year")).isEqualTo(2020);
        assertThat(retrieved.get().stringListField("authors")).contains("Lewis", "Petroni");
        assertThat(retrieved.get().stringListField("citations")).containsExactly("lit-001");

        assertThat(harness.catalogue().forType(LiteratureCorpusTypes.LITERATURE, TENANT)).hasSize(1);

        harness.catalogue().updateLifecycle(corpus, CorpusLifecycle.ARCHIVED);
        assertThat(harness.catalogue().list(TENANT, CorpusLifecycle.ACTIVE)).isEmpty();
        harness.catalogue().updateLifecycle(corpus, CorpusLifecycle.ACTIVE);
        assertThat(harness.catalogue().list(TENANT, CorpusLifecycle.ACTIVE)).hasSize(1);
    }
}
