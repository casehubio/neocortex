package io.casehub.neocortex.rag.spring;

import io.casehub.neocortex.fusion.FusionStrategy;
import io.casehub.neocortex.inference.MatryoshkaMultiModalEmbedder;
import io.casehub.neocortex.inference.MultiModalEmbedder;
import io.casehub.neocortex.rag.CursorStore;
import io.casehub.neocortex.rag.EmbeddingIngestor;
import io.casehub.neocortex.rag.MetadataExtractor;
import io.casehub.neocortex.rag.runtime.BM25IndexRegistry;
import io.casehub.neocortex.rag.runtime.CorpusIngestionService;
import io.casehub.neocortex.rag.runtime.DenseQuantization;
import io.casehub.neocortex.rag.runtime.FileCursorStore;
import io.casehub.neocortex.rag.runtime.HybridCaseRetriever;
import io.casehub.neocortex.rag.runtime.RagConfig;
import io.casehub.neocortex.rag.runtime.TenancyStrategy;
import io.casehub.neocortex.rag.runtime.TenantGuard;
import io.casehub.neocortex.rag.runtime.YamlFrontmatterExtractor;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.qdrant.client.QdrantClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

@AutoConfiguration
@ConditionalOnClass(HybridCaseRetriever.class)
public class RagAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RagConfig ragConfig(
            @Value("${casehub.rag.qdrant.host:localhost}") String qdrantHost,
            @Value("${casehub.rag.qdrant.port:6334}") int qdrantPort,
            @Value("${casehub.rag.qdrant.api-key:#{null}}") String qdrantApiKey,
            @Value("${casehub.rag.qdrant.use-tls:false}") boolean qdrantUseTls,
            @Value("${casehub.rag.tenancy-strategy:SEPARATE_COLLECTIONS}") TenancyStrategy tenancyStrategy,
            @Value("${casehub.rag.dense-vector-name:dense}") String denseVectorName,
            @Value("${casehub.rag.sparse-vector-name:sparse}") String sparseVectorName,
            @Value("${casehub.rag.bm25-enabled:true}") boolean bm25Enabled,
            @Value("${casehub.rag.bm25-vector-name:bm25}") String bm25VectorName,
            @Value("${casehub.rag.colbert-vector-name:colbert}") String colbertVectorName,
            @Value("${casehub.rag.embedding-batch-size:100}") int embeddingBatchSize,
            @Value("${casehub.rag.max-multivector-floats:1000000}") int maxMultivectorFloats,
            @Value("${casehub.rag.max-sequence-length:#{null}}") Integer maxSequenceLength,
            @Value("${casehub.rag.matryoshka.dimension:#{null}}") Integer matryoshkaDimension,
            @Value("${casehub.rag.retrieval.fusion-strategy:RRF}") FusionStrategy fusionStrategy,
            @Value("${casehub.rag.retrieval.dense-top-k:40}") int denseTopK,
            @Value("${casehub.rag.retrieval.sparse-top-k:40}") int sparseTopK,
            @Value("${casehub.rag.retrieval.bm25-top-k:40}") int bm25TopK,
            @Value("${casehub.rag.retrieval.rrf-k:60}") int rrfK,
            @Value("${casehub.rag.reranking.colbert-fallback:true}") boolean rerankEnabled,
            @Value("${casehub.rag.retrieval.rerank-top-n:10}") int rerankTopN,
            @Value("${casehub.rag.retrieval.weights.dense:1.0}") double weightsDense,
            @Value("${casehub.rag.retrieval.weights.sparse:1.0}") double weightsSparse,
            @Value("${casehub.rag.retrieval.weights.bm25:1.0}") double weightsBm25,
            @Value("${casehub.rag.retrieval.weights.quality:0.0}") double weightsQuality,
            @Value("${casehub.rag.retrieval.quality-payload-field:#{null}}") String qualityPayloadField,
            @Value("${casehub.rag.retrieval.quality-max:10.0}") double qualityMax,
            @Value("${casehub.rag.quantization.type:NONE}") DenseQuantization quantizationType,
            @Value("${casehub.rag.quantization.always-ram:true}") boolean quantizationAlwaysRam,
            @Value("${casehub.rag.quantization.oversampling:#{null}}") Double quantizationOversampling,
            @Value("${casehub.rag.colbert-quantization.type:NONE}") DenseQuantization colbertQuantizationType,
            @Value("${casehub.rag.colbert-quantization.always-ram:true}") boolean colbertQuantizationAlwaysRam) {

        return new SpringRagConfig(
                new SpringRagConfig.QdrantConfigImpl(qdrantHost, qdrantPort, Optional.ofNullable(qdrantApiKey), qdrantUseTls),
                tenancyStrategy,
                denseVectorName,
                sparseVectorName,
                bm25Enabled,
                bm25VectorName,
                colbertVectorName,
                new SpringRagConfig.RetrievalConfigImpl(
                        fusionStrategy, denseTopK, sparseTopK, bm25TopK, rrfK,
                        rerankEnabled, rerankTopN,
                        new SpringRagConfig.FusionWeightsConfigImpl(weightsDense, weightsSparse, weightsBm25, weightsQuality),
                        Optional.ofNullable(qualityPayloadField),
                        qualityMax),
                embeddingBatchSize,
                maxMultivectorFloats,
                Optional.ofNullable(maxSequenceLength),
                new SpringRagConfig.MatryoshkaConfigImpl(
                        matryoshkaDimension != null ? OptionalInt.of(matryoshkaDimension) : OptionalInt.empty()),
                new SpringRagConfig.QuantizationConfigImpl(
                        quantizationType, quantizationAlwaysRam,
                        quantizationOversampling != null ? OptionalDouble.of(quantizationOversampling) : OptionalDouble.empty()),
                new SpringRagConfig.ColbertQuantizationConfigImpl(colbertQuantizationType, colbertQuantizationAlwaysRam));
    }

    @Bean
    public BM25IndexRegistry bm25IndexRegistry(RagConfig ragConfig) {
        return new BM25IndexRegistry(ragConfig.tenancyStrategy());
    }

    @Bean
    public HybridCaseRetriever hybridCaseRetriever(QdrantClient client,
                                                    MultiModalEmbedder embedder,
                                                    Optional<CurrentPrincipal> currentPrincipal,
                                                    RagConfig config) {
        return new HybridCaseRetriever(client,
                MatryoshkaMultiModalEmbedder.wrapIfNeeded(embedder, config.matryoshka().dimension()),
                TenantGuard.of(currentPrincipal.orElse(null)),
                config);
    }

    @Bean
    @ConditionalOnMissingBean
    public CursorStore fileCursorStore(
            @Value("${casehub.rag.ingestion.cursor-dir:${java.io.tmpdir}/casehub-ingestion-cursors}") String cursorDir) {
        return new FileCursorStore(cursorDir);
    }

    @Bean
    @ConditionalOnMissingBean
    public MetadataExtractor yamlFrontmatterExtractor() {
        return new YamlFrontmatterExtractor();
    }

    @Bean
    public CorpusIngestionService corpusIngestionService(EmbeddingIngestor ingestor,
                                                          CursorStore cursorStore) {
        return new CorpusIngestionService(ingestor, cursorStore);
    }
}
