package io.casehub.neocortex.rag.runtime;

import io.casehub.neocortex.fusion.FusionStrategy;
import io.casehub.neocortex.rag.CaseRetriever;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PayloadBoostCaseRetrieverTest {

    private static final CorpusRef CORPUS = new CorpusRef("t", "c");

    private static RetrievedChunk chunk(String id, double score, String qualityValue) {
        Map<String, String> metadata = qualityValue != null
            ? Map.of("score", qualityValue) : Map.of();
        return new RetrievedChunk("content-" + id, id, score, metadata);
    }

    private static CaseRetriever stubRetriever(List<RetrievedChunk> results) {
        return (query, corpus, maxResults, filter) -> results;
    }

    private static RagConfig boostConfig(FusionStrategy strategy, double qualityWeight,
                                          String qualityField, double qualityMax) {
        return new RagConfig() {
            @Override public QdrantConfig qdrant() {
                return new QdrantConfig() {
                    @Override public String host() { return "localhost"; }
                    @Override public int port() { return 6334; }
                    @Override public Optional<String> apiKey() { return Optional.empty(); }
                    @Override public boolean useTls() { return false; }
                };
            }
            @Override public TenancyStrategy tenancyStrategy() { return TenancyStrategy.SEPARATE_COLLECTIONS; }
            @Override public String denseVectorName() { return "dense"; }
            @Override public String sparseVectorName() { return "sparse"; }
            @Override public boolean bm25Enabled() { return false; }
            @Override public String bm25VectorName() { return "bm25"; }
            @Override public String colbertVectorName() { return "colbert"; }
            @Override public RetrievalConfig retrieval() {
                return new RetrievalConfig() {
                    @Override public FusionStrategy fusionStrategy() { return strategy; }
                    @Override public int denseTopK() { return 40; }
                    @Override public int sparseTopK() { return 40; }
                    @Override public int bm25TopK() { return 40; }
                    @Override public int rrfK() { return 60; }
                    @Override public boolean rerankEnabled() { return false; }
                    @Override public int rerankTopN() { return 10; }
                    @Override public FusionWeightsConfig weights() {
                        return new FusionWeightsConfig() {
                            @Override public double dense() { return 1.0; }
                            @Override public double sparse() { return 1.0; }
                            @Override public double bm25() { return 1.0; }
                            @Override public double quality() { return qualityWeight; }
                        };
                    }
                    @Override public Optional<String> qualityPayloadField() {
                        return qualityField != null ? Optional.of(qualityField) : Optional.empty();
                    }
                    @Override public double qualityMax() { return qualityMax; }
                };
            }
            @Override public int embeddingBatchSize() { return 100; }
            @Override public int maxMultivectorFloats() { return 1_000_000; }
            @Override public Optional<Integer> maxSequenceLength() { return Optional.of(512); }
            @Override public MatryoshkaConfig matryoshka() {
                return () -> OptionalInt.empty();
            }
            @Override public QuantizationConfig quantization() {
                return new QuantizationConfig() {
                    @Override public DenseQuantization type() { return DenseQuantization.NONE; }
                    @Override public boolean alwaysRam() { return true; }
                    @Override public OptionalDouble oversampling() { return OptionalDouble.empty(); }
                };
            }
            @Override public ColbertQuantizationConfig colbertQuantization() {
                return new ColbertQuantizationConfig() {
                    @Override public DenseQuantization type() { return DenseQuantization.NONE; }
                    @Override public boolean alwaysRam() { return true; }
                };
            }
        };
    }

    @Test
    void rrfStrategy_boostsHighQualityDocuments() {
        var chunks = List.of(chunk("low-q", 0.8, "3"), chunk("high-q", 0.7, "9"));
        var decorator = new PayloadBoostCaseRetriever(
            stubRetriever(chunks), boostConfig(FusionStrategy.RRF, 0.5, "score", 10.0));

        var result = decorator.retrieve(RetrievalQuery.of("test"), CORPUS, 10, null);

        assertThat(result.get(0).sourceDocumentId()).isEqualTo("high-q");
    }

    @Test
    void ccStrategy_isNoOp() {
        var chunks = List.of(chunk("a", 0.8, "3"), chunk("b", 0.7, "9"));
        var decorator = new PayloadBoostCaseRetriever(
            stubRetriever(chunks), boostConfig(FusionStrategy.CC, 0.5, "score", 10.0));

        var result = decorator.retrieve(RetrievalQuery.of("test"), CORPUS, 10, null);

        assertThat(result.get(0).sourceDocumentId()).isEqualTo("a");
        assertThat(result.get(0).relevanceScore()).isEqualTo(0.8);
    }

    @Test
    void qualityWeightZero_isNoOp() {
        var chunks = List.of(chunk("a", 0.7, "9"), chunk("b", 0.8, "3"));
        var decorator = new PayloadBoostCaseRetriever(
                stubRetriever(chunks), boostConfig(FusionStrategy.RRF, 0.0, null, 10.0));

        var result = decorator.retrieve(RetrievalQuery.of("test"), CORPUS, 10, null);

        assertThat(result.get(0).relevanceScore()).isEqualTo(0.7);
        assertThat(result.get(1).relevanceScore()).isEqualTo(0.8);
    }

    @Test
    void missingQualityField_retainsOriginalScore() {
        var chunks = List.of(chunk("no-field", 0.8, null), chunk("has-field", 0.7, "9"));
        var decorator = new PayloadBoostCaseRetriever(
            stubRetriever(chunks), boostConfig(FusionStrategy.RRF, 0.5, "score", 10.0));

        var result = decorator.retrieve(RetrievalQuery.of("test"), CORPUS, 10, null);

        var noField = result.stream().filter(c -> c.sourceDocumentId().equals("no-field")).findFirst().orElseThrow();
        assertThat(noField.relevanceScore()).isEqualTo(0.8);
    }

    @Test
    void nonNumericField_retainsOriginalScore() {
        var chunks = List.of(new RetrievedChunk("content", "bad", 0.8, Map.of("score", "not-a-number")));
        var decorator = new PayloadBoostCaseRetriever(
            stubRetriever(chunks), boostConfig(FusionStrategy.RRF, 0.5, "score", 10.0));

        var result = decorator.retrieve(RetrievalQuery.of("test"), CORPUS, 10, null);

        assertThat(result.get(0).relevanceScore()).isEqualTo(0.8);
    }

    @Test
    void qualityMaxClampsNormalization() {
        var chunks = List.of(chunk("over-max", 0.5, "20"));
        var decorator = new PayloadBoostCaseRetriever(
            stubRetriever(chunks), boostConfig(FusionStrategy.RRF, 1.0, "score", 10.0));

        var result = decorator.retrieve(RetrievalQuery.of("test"), CORPUS, 10, null);

        double expected = 0.5 * (1 + 1.0 * 1.0);
        assertThat(result.get(0).relevanceScore()).isCloseTo(expected, within(0.001));
    }

    @Test
    void noQualityPayloadFieldConfigured_isNoOp() {
        var chunks = List.of(chunk("a", 0.8, "9"));
        var decorator = new PayloadBoostCaseRetriever(
            stubRetriever(chunks), boostConfig(FusionStrategy.RRF, 0.5, null, 10.0));

        var result = decorator.retrieve(RetrievalQuery.of("test"), CORPUS, 10, null);

        assertThat(result.get(0).relevanceScore()).isEqualTo(0.8);
    }
}
