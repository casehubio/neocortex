package io.casehub.neocortex.rag.expansion;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.casehub.neocortex.rag.CaseRetriever;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.QueryExpander;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.neocortex.rag.testing.InMemoryQueryExpander;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class QueryExpandingCaseRetrieverTest {

    private static final CorpusRef CORPUS = new CorpusRef("tenant-1", "test-corpus");

    @Test
    void delegatesWithExpandedQuery() {
        var capturedQueries = new ArrayList<RetrievalQuery>();
        CaseRetriever delegate = (query, corpus, maxResults, filter) -> {
            capturedQueries.add(query);
            return List.of(chunk(query.searchText(), "doc-" + capturedQueries.size(), 0.9));
        };
        var expander = new InMemoryQueryExpander();

        var retriever = new QueryExpandingCaseRetriever(delegate, expander);
        var results = retriever.retrieve(RetrievalQuery.of("original"), CORPUS, 10, null);

        // Original + expanded = 2 queries fanned out
        assertThat(capturedQueries).hasSize(2);
        // First query is the original (no expansion)
        assertThat(capturedQueries.get(0).text()).isEqualTo("original");
        assertThat(capturedQueries.get(0).expandedText()).isNull();
        // Second is the expanded
        assertThat(capturedQueries.get(1).text()).isEqualTo("original");
        assertThat(capturedQueries.get(1).expandedText()).isEqualTo("hypothetical: original");
        assertThat(capturedQueries.get(1).searchText()).isEqualTo("hypothetical: original");
        // Results fused via RRF
        assertThat(results).hasSize(2);
    }

    @Test
    void failSafeOnExpanderError() {
        var capturedQuery = new AtomicReference<RetrievalQuery>();
        CaseRetriever delegate = (query, corpus, maxResults, filter) -> {
            capturedQuery.set(query);
            return List.of(chunk("result", "doc1", 0.9));
        };
        QueryExpander failingExpander = query -> {
            throw new RuntimeException("LLM timeout");
        };

        var retriever = new QueryExpandingCaseRetriever(delegate, failingExpander);
        var results = retriever.retrieve(RetrievalQuery.of("original"), CORPUS, 10, null);

        assertThat(results).hasSize(1);
        assertThat(capturedQuery.get().text()).isEqualTo("original");
        assertThat(capturedQuery.get().expandedText()).isNull();
    }

    @Test
    void passesCorpusAndFilterThrough() {
        var capturedCorpus = new AtomicReference<CorpusRef>();
        var capturedMax = new int[1];
        CaseRetriever delegate = (query, corpus, maxResults, filter) -> {
            capturedCorpus.set(corpus);
            capturedMax[0] = maxResults;
            return List.of();
        };

        var retriever = new QueryExpandingCaseRetriever(delegate, new InMemoryQueryExpander());
        retriever.retrieve(RetrievalQuery.of("q"), CORPUS, 7, null);

        assertThat(capturedCorpus.get()).isEqualTo(CORPUS);
        assertThat(capturedMax[0]).isEqualTo(7);
    }

    @Test
    void multiQueryFansOutAndMergesViaRrf() {
        var capturedQueries = new ArrayList<RetrievalQuery>();
        CaseRetriever delegate = (query, corpus, maxResults, filter) -> {
            capturedQueries.add(query);
            return List.of(chunk(query.searchText(), "doc-" + capturedQueries.size(), 0.9));
        };
        QueryExpander multiExpander = query -> List.of(
            query,
            RetrievalQuery.of("abstract version")
        );

        var decorator = new QueryExpandingCaseRetriever(delegate, multiExpander);
        var results = decorator.retrieve(RetrievalQuery.of("original"), CORPUS, 10, null);

        assertThat(capturedQueries).hasSize(2);
        assertThat(capturedQueries.get(0).text()).isEqualTo("original");
        assertThat(capturedQueries.get(1).text()).isEqualTo("abstract version");
        assertThat(results).hasSize(2);
    }

    @Test
    void singleExpandedQueryGetsPrependedOriginal() {
        var capturedQueries = new ArrayList<RetrievalQuery>();
        CaseRetriever delegate = (query, corpus, maxResults, filter) -> {
            capturedQueries.add(query);
            return List.of(chunk(query.searchText(), "doc-" + capturedQueries.size(), 0.9));
        };
        QueryExpander singleExpander = query -> List.of(query.withExpansion("expanded"));

        var decorator = new QueryExpandingCaseRetriever(delegate, singleExpander);
        var results = decorator.retrieve(RetrievalQuery.of("original"), CORPUS, 10, null);

        // Original prepended + expanded = 2 queries, RRF fusion
        assertThat(capturedQueries).hasSize(2);
        assertThat(capturedQueries.get(0).expandedText()).isNull();
        assertThat(capturedQueries.get(1).expandedText()).isEqualTo("expanded");
        assertThat(results).hasSize(2);
    }

    @Test
    void emptyExpansionFallsBackToOriginal() {
        var capturedQueries = new ArrayList<RetrievalQuery>();
        CaseRetriever delegate = (query, corpus, maxResults, filter) -> {
            capturedQueries.add(query);
            return List.of(chunk("result", "doc1", 0.9));
        };
        QueryExpander emptyExpander = query -> List.of();

        var decorator = new QueryExpandingCaseRetriever(delegate, emptyExpander);
        var results = decorator.retrieve(RetrievalQuery.of("original"), CORPUS, 10, null);

        assertThat(capturedQueries).hasSize(1);
        assertThat(capturedQueries.get(0).text()).isEqualTo("original");
        assertThat(capturedQueries.get(0).expandedText()).isNull();
        assertThat(results).hasSize(1);
    }

    @Test
    void prependsOriginalWhenExpanderOmitsIt() {
        var capturedQueries = new ArrayList<RetrievalQuery>();
        CaseRetriever delegate = (query, corpus, maxResults, filter) -> {
            capturedQueries.add(query);
            return List.of(chunk(query.searchText(), "doc-" + capturedQueries.size(), 0.9));
        };
        // Expander returns only the expanded query (like LlmQueryExpander)
        QueryExpander hydeExpander = query -> List.of(query.withExpansion("hypothetical"));

        var decorator = new QueryExpandingCaseRetriever(delegate, hydeExpander);
        var results = decorator.retrieve(RetrievalQuery.of("original"), CORPUS, 10, null);

        // Original + expanded = 2 queries fanned out
        assertThat(capturedQueries).hasSize(2);
        // First query is the original (no expansion)
        assertThat(capturedQueries.get(0).expandedText()).isNull();
        assertThat(capturedQueries.get(0).text()).isEqualTo("original");
        // Second is the expanded
        assertThat(capturedQueries.get(1).expandedText()).isEqualTo("hypothetical");
        assertThat(results).hasSize(2);
    }

    @Test
    void doesNotDuplicateOriginalWhenExpanderIncludesIt() {
        var capturedQueries = new ArrayList<RetrievalQuery>();
        CaseRetriever delegate = (query, corpus, maxResults, filter) -> {
            capturedQueries.add(query);
            return List.of(chunk(query.searchText(), "doc-" + capturedQueries.size(), 0.9));
        };
        // StepBack-style: expander already includes original
        var original = RetrievalQuery.of("original");
        QueryExpander stepBackExpander = query -> List.of(query, RetrievalQuery.of("abstract"));

        var decorator = new QueryExpandingCaseRetriever(delegate, stepBackExpander);
        decorator.retrieve(original, CORPUS, 10, null);

        assertThat(capturedQueries).hasSize(2);
        assertThat(capturedQueries.get(0)).isEqualTo(original);
    }

    @Test
    void prependsOriginalForReformulatedQueryWithoutExpansion() {
        var capturedQueries = new ArrayList<RetrievalQuery>();
        CaseRetriever delegate = (query, corpus, maxResults, filter) -> {
            capturedQueries.add(query);
            return List.of(chunk(query.searchText(), "doc-" + capturedQueries.size(), 0.9));
        };
        // Custom expander returns a reformulated query (no withExpansion)
        QueryExpander reformulator = query -> List.of(RetrievalQuery.of("reformulated"));

        var original = RetrievalQuery.of("original");
        var decorator = new QueryExpandingCaseRetriever(delegate, reformulator);
        decorator.retrieve(original, CORPUS, 10, null);

        // Original prepended because contains() uses record equality
        assertThat(capturedQueries).hasSize(2);
        assertThat(capturedQueries.get(0)).isEqualTo(original);
        assertThat(capturedQueries.get(1).text()).isEqualTo("reformulated");
    }

    @Test
    void driftObserveModeKeepsAllExpansions() {
        var capturedQueries = new ArrayList<RetrievalQuery>();
        CaseRetriever delegate = (query, corpus, maxResults, filter) -> {
            capturedQueries.add(query);
            return List.of(chunk(query.searchText(), "doc-" + capturedQueries.size(), 0.9));
        };
        QueryExpander driftingExpander = query -> List.of(query.withExpansion("completely unrelated text"));

        var retriever = new QueryExpandingCaseRetriever(delegate, driftingExpander);
        retriever.embeddingModel = ExpansionConfigValidatorTest.presentInstance(
                new StubEmbeddingModel(Map.of(
                        "original", new float[]{1.0f, 0.0f, 0.0f},
                        "completely unrelated text", new float[]{0.0f, 1.0f, 0.0f})));
        retriever.meterRegistry  = ExpansionConfigValidatorTest.emptyInstance();
        retriever.config         = stubExpansionConfig(true, 0.7, DriftAction.OBSERVE);

        var results = retriever.retrieve(RetrievalQuery.of("original"), CORPUS, 10, null);
        assertThat(capturedQueries).hasSize(2);
        assertThat(results).hasSize(2);
    }

    @Test
    void driftDropModeRemovesDriftedExpansions() {
        var capturedQueries = new ArrayList<RetrievalQuery>();
        CaseRetriever delegate = (query, corpus, maxResults, filter) -> {
            capturedQueries.add(query);
            return List.of(chunk(query.searchText(), "doc-" + capturedQueries.size(), 0.9));
        };
        QueryExpander driftingExpander = query -> List.of(query.withExpansion("completely unrelated text"));

        var retriever = new QueryExpandingCaseRetriever(delegate, driftingExpander);
        retriever.embeddingModel = ExpansionConfigValidatorTest.presentInstance(
                new StubEmbeddingModel(Map.of(
                        "original", new float[]{1.0f, 0.0f, 0.0f},
                        "completely unrelated text", new float[]{0.0f, 1.0f, 0.0f})));
        retriever.meterRegistry  = ExpansionConfigValidatorTest.emptyInstance();
        retriever.config         = stubExpansionConfig(true, 0.7, DriftAction.DROP);

        var results = retriever.retrieve(RetrievalQuery.of("original"), CORPUS, 10, null);
        assertThat(capturedQueries).hasSize(1);
        assertThat(capturedQueries.get(0).expandedText()).isNull();
    }

    @Test
    void originalQueryExcludedFromDriftComparison() {
        var capturedQueries = new ArrayList<RetrievalQuery>();
        CaseRetriever delegate = (query, corpus, maxResults, filter) -> {
            capturedQueries.add(query);
            return List.of(chunk(query.searchText(), "doc-" + capturedQueries.size(), 0.9));
        };
        QueryExpander expander = query -> List.of(query.withExpansion("similar enough text"));

        var retriever = new QueryExpandingCaseRetriever(delegate, expander);
        retriever.embeddingModel = ExpansionConfigValidatorTest.presentInstance(
                new StubEmbeddingModel(Map.of(
                        "original", new float[]{1.0f, 0.0f, 0.0f},
                        "similar enough text", new float[]{0.9f, 0.44f, 0.0f})));
        retriever.meterRegistry  = ExpansionConfigValidatorTest.emptyInstance();
        retriever.config         = stubExpansionConfig(true, 0.7, DriftAction.DROP);

        var results = retriever.retrieve(RetrievalQuery.of("original"), CORPUS, 10, null);
        assertThat(capturedQueries).hasSize(2);
        assertThat(capturedQueries.get(0).expandedText()).isNull();
        assertThat(capturedQueries.get(1).expandedText()).isEqualTo("similar enough text");
    }

    @Test
    void embeddingFailureFallsBackToUnfilteredList() {
        var capturedQueries = new ArrayList<RetrievalQuery>();
        CaseRetriever delegate = (query, corpus, maxResults, filter) -> {
            capturedQueries.add(query);
            return List.of(chunk(query.searchText(), "doc-" + capturedQueries.size(), 0.9));
        };
        QueryExpander expander = query -> List.of(query.withExpansion("expanded text"));

        EmbeddingModel failingModel = segments -> {throw new RuntimeException("ONNX error");};
        var            retriever    = new QueryExpandingCaseRetriever(delegate, expander);
        retriever.embeddingModel = ExpansionConfigValidatorTest.presentInstance(failingModel);
        retriever.meterRegistry  = ExpansionConfigValidatorTest.emptyInstance();
        retriever.config         = stubExpansionConfig(true, 0.7, DriftAction.DROP);

        var results = retriever.retrieve(RetrievalQuery.of("original"), CORPUS, 10, null);
        assertThat(capturedQueries).hasSize(2);
    }

    @Test
    void noEmbeddingModelSkipsDriftDetection() {
        var capturedQueries = new ArrayList<RetrievalQuery>();
        CaseRetriever delegate = (query, corpus, maxResults, filter) -> {
            capturedQueries.add(query);
            return List.of(chunk(query.searchText(), "doc-" + capturedQueries.size(), 0.9));
        };
        QueryExpander expander = query -> List.of(query.withExpansion("expanded text"));

        var retriever = new QueryExpandingCaseRetriever(delegate, expander);
        retriever.embeddingModel = ExpansionConfigValidatorTest.emptyInstance();
        retriever.meterRegistry  = ExpansionConfigValidatorTest.emptyInstance();
        retriever.config         = stubExpansionConfig(true, 0.7, DriftAction.DROP);

        var results = retriever.retrieve(RetrievalQuery.of("original"), CORPUS, 10, null);
        assertThat(capturedQueries).hasSize(2);
    }

    @Test
    void driftDisabledSkipsDriftDetection() {
        var capturedQueries = new ArrayList<RetrievalQuery>();
        CaseRetriever delegate = (query, corpus, maxResults, filter) -> {
            capturedQueries.add(query);
            return List.of(chunk(query.searchText(), "doc-" + capturedQueries.size(), 0.9));
        };
        QueryExpander driftingExpander = query -> List.of(query.withExpansion("completely unrelated"));

        var retriever = new QueryExpandingCaseRetriever(delegate, driftingExpander);
        retriever.embeddingModel = ExpansionConfigValidatorTest.presentInstance(
                new StubEmbeddingModel(Map.of(
                        "original", new float[]{1.0f, 0.0f, 0.0f},
                        "completely unrelated", new float[]{0.0f, 1.0f, 0.0f})));
        retriever.meterRegistry  = ExpansionConfigValidatorTest.emptyInstance();
        retriever.config         = stubExpansionConfig(false, 0.7, DriftAction.DROP);

        var results = retriever.retrieve(RetrievalQuery.of("original"), CORPUS, 10, null);
        assertThat(capturedQueries).hasSize(2);
    }

    @Test
    void driftDetectionUsesSingleBatchEmbedCall() {
        var embedCallCount = new int[]{0};
        EmbeddingModel countingModel = segments -> {
            embedCallCount[0]++;
            List<Embedding> embeddings = segments.stream()
                                                 .map(s -> Embedding.from(new float[]{1.0f, 0.0f, 0.0f}))
                                                 .toList();
            return Response.from(embeddings);
        };
        CaseRetriever delegate = (query, corpus, maxResults, filter) ->
                                         List.of(chunk(query.searchText(), "doc", 0.9));
        QueryExpander expander = query -> List.of(
                query.withExpansion("exp1"), query.withExpansion("exp2"));

        var retriever = new QueryExpandingCaseRetriever(delegate, expander);
        retriever.embeddingModel = ExpansionConfigValidatorTest.presentInstance(countingModel);
        retriever.meterRegistry  = ExpansionConfigValidatorTest.emptyInstance();
        retriever.config         = stubExpansionConfig(true, 0.7, DriftAction.OBSERVE);

        retriever.retrieve(RetrievalQuery.of("original"), CORPUS, 10, null);
        assertThat(embedCallCount[0]).isEqualTo(1);
    }

    private static ExpansionConfig stubExpansionConfig(boolean driftEnabled, double threshold, DriftAction action) {
        return ExpansionConfigValidatorTest.stubConfig(
                java.util.Optional.of("llm"),
                new ExpansionConfig.DriftConfig() {
                    @Override
                    public boolean enabled()    {return driftEnabled;}

                    @Override
                    public double threshold()   {return threshold;}

                    @Override
                    public DriftAction action() {return action;}
                });
    }

    private static class StubEmbeddingModel implements EmbeddingModel {
        private final Map<String, float[]> vectors;

        StubEmbeddingModel(Map<String, float[]> vectors) {
            this.vectors = vectors;
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            List<Embedding> embeddings = textSegments.stream()
                                                     .map(s -> {
                                                         float[] vec = vectors.getOrDefault(s.text(), new float[]{0.5f, 0.5f, 0.5f});
                                                         return Embedding.from(vec);
                                                     })
                                                     .toList();
            return Response.from(embeddings);
        }
    }


    private static RetrievedChunk chunk(String content, String docId, double score) {
        return new RetrievedChunk(content, docId, score, Map.of());
    }
}
