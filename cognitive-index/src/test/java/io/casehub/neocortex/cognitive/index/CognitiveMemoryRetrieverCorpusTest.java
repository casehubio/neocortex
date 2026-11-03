package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusQuery;
import io.casehub.neocortex.corpus.management.CorpusSearchResult;
import io.casehub.neocortex.corpus.management.FederatedCorpusSearch;
import io.casehub.neocortex.corpus.management.FederatedSearchResult;
import io.casehub.neocortex.corpus.management.SearchReport;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.rag.CorpusRef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CognitiveMemoryRetrieverCorpusTest {

    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private static final CorpusRef CORPUS_REF = new CorpusRef("tenant-1", "agent-01-interactions");

    @Test
    void queryCorporaPreservesSearchPipelineOrdering() {
        var first = corpusResult("entry-1", NOW.minus(60, ChronoUnit.DAYS), 0.95);
        var second = corpusResult("entry-2", NOW.minus(1, ChronoUnit.HOURS), 0.9);

        var search = stubSearch(List.of(first, second));
        var retriever = new CognitiveMemoryRetriever(emptyStore(), List.of(), List.of(), List.of(), search);

        var ctx = new ModulationContext(null, "tenant-1", null, null, null);
        var results = retriever.queryCorpora(CorpusQuery.of("test"), "tenant-1", ctx);

        assertThat(results).extracting(r -> r.entry().id())
            .containsExactly("entry-1", "entry-2");
    }

    @Test
    void queryCorporaReturnsEmptyWhenNoSearch() {
        var retriever = new CognitiveMemoryRetriever(emptyStore(), List.of());

        var ctx = new ModulationContext(null, "tenant-1", null, null, null);
        var results = retriever.queryCorpora(CorpusQuery.of("test"), "tenant-1", ctx);

        assertThat(results).isEmpty();
    }

    @Test
    void queryCorporaPassesThroughEmptyResults() {
        var search = stubSearch(List.of());
        var retriever = new CognitiveMemoryRetriever(emptyStore(), List.of(), List.of(), List.of(), search);

        var ctx = new ModulationContext(null, "tenant-1", null, null, null);
        var results = retriever.queryCorpora(CorpusQuery.of("nothing"), "tenant-1", ctx);

        assertThat(results).isEmpty();
    }

    private static CorpusSearchResult corpusResult(String id, Instant timestamp, double score) {
        var entry = new CorpusEntry(id, Map.of("title", "Test " + id, "type", "transcript",
            "corpus", "agent-01-interactions", "interaction_type", "agent-human",
            "activity_type", "debugging", "artifact_ref", "corpus://test/t.zip#" + id),
            "Body of " + id, "corpus://test/t.zip#" + id);
        return new CorpusSearchResult(entry, score, CORPUS_REF, 0, timestamp);
    }

    private static FederatedCorpusSearch stubSearch(List<CorpusSearchResult> results) {
        return (query, tenantId) -> new FederatedSearchResult(results,
            new SearchReport(List.of(CORPUS_REF), List.of(), false));
    }

    private static CaseMemoryStore emptyStore() {
        return new CaseMemoryStore() {
            @Override public String store(MemoryInput input) { throw new UnsupportedOperationException(); }
            @Override public List<Memory> query(MemoryQuery query) { return List.of(); }
            @Override public int erase(EraseRequest request) { throw new UnsupportedOperationException(); }
        };
    }
}
