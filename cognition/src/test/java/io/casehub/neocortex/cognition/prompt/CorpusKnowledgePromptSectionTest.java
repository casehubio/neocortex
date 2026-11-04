package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.cognitive.index.CognitiveMemoryRetriever;
import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusSearchResult;
import io.casehub.neocortex.corpus.management.FederatedSearchResult;
import io.casehub.neocortex.corpus.management.SearchReport;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.rag.CorpusRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

class CorpusKnowledgePromptSectionTest {

    private static final CorpusRef CORPUS_A = new CorpusRef("t1", "research-corpus");
    private static final CorpusRef CORPUS_B = new CorpusRef("t1", "platform-garden");

    @Test
    void rendersGroupedByCorpus() {
        var results = List.of(
            corpusResult("CE-1", "Sleep consolidation", CORPUS_A),
            corpusResult("CE-2", "REM integration", CORPUS_A),
            corpusResult("GE-1", "SmallRye config gotcha", CORPUS_B)
        );
        var retriever = retrieverWith(results);
        var section = new CorpusKnowledgePromptSection(retriever, "memory consolidation");

        var ctx = new CognitionRenderContext("agent-01", "t1", null);
        String rendered = section.render(ctx);

        assertThat(rendered).isNotNull();
        assertThat(rendered).contains("## Relevant Knowledge");
        assertThat(rendered).contains("research-corpus");
        assertThat(rendered).contains("Sleep consolidation [CE-1]");
        assertThat(rendered).contains("REM integration [CE-2]");
        assertThat(rendered).contains("platform-garden");
        assertThat(rendered).contains("SmallRye config gotcha [GE-1]");
    }

    @Test
    void returnsNullWhenNoResults() {
        var retriever = retrieverWith(List.of());
        var section = new CorpusKnowledgePromptSection(retriever, "nothing relevant");

        var ctx = new CognitionRenderContext("agent-01", "t1", null);
        String rendered = section.render(ctx);

        assertThat(rendered).isNull();
    }

    @Test
    void returnsNullWhenNoFederatedSearch() {
        var retriever = new CognitiveMemoryRetriever(emptyStore(), List.of());
        var section = new CorpusKnowledgePromptSection(retriever, "test");

        var ctx = new CognitionRenderContext("agent-01", "t1", null);
        String rendered = section.render(ctx);

        assertThat(rendered).isNull();
    }

    private static CorpusSearchResult corpusResult(String id, String title, CorpusRef corpus) {
        var entry = new CorpusEntry(id, Map.of("title", title, "type", "research-paper",
            "corpus", corpus.corpusName(), "domain", "test",
            "artifact_ref", "corpus://test/t.zip#" + id),
            "Body", "corpus://test/t.zip#" + id);
        return new CorpusSearchResult(entry, 0.85, corpus, 0, Instant.now());
    }

    private static CognitiveMemoryRetriever retrieverWith(List<CorpusSearchResult> results) {
        return new CognitiveMemoryRetriever(
                emptyStore(), List.of(), List.of(), List.of(),
                (query, tenantId) -> new FederatedSearchResult(results,
                                                               new SearchReport(List.of(), List.of(), false)));
    }

    private static CaseMemoryStore emptyStore() {
        return new CaseMemoryStore() {
            @Override public String store(MemoryInput input) { throw new UnsupportedOperationException(); }
            @Override public List<Memory> query(MemoryQuery query) { return List.of(); }
            @Override public int erase(EraseRequest request) { throw new UnsupportedOperationException(); }
        };
    }
}
