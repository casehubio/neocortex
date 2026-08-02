package io.casehub.neocortex.rag.runtime;

import io.casehub.neocortex.fusion.CamelCaseExpander;
import io.casehub.neocortex.fusion.FusionStrategy;
import io.casehub.neocortex.fusion.ScoreFusion;
import io.casehub.neocortex.inference.EmbeddingMode;
import io.casehub.neocortex.inference.MatryoshkaMultiModalEmbedder;
import io.casehub.neocortex.inference.MultiModalEmbedder;
import io.casehub.neocortex.inference.MultiModalEmbedding;
import io.casehub.neocortex.rag.CaseRetriever;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.PayloadFilter;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievedChunk;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QueryFactory;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.Document;
import io.qdrant.client.grpc.Points.Fusion;
import io.qdrant.client.grpc.Points.PrefetchQuery;
import io.qdrant.client.grpc.Points.QuantizationSearchParams;
import io.qdrant.client.grpc.Points.QueryPoints;
import io.qdrant.client.grpc.Points.Rrf;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchParams;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class HybridCaseRetriever implements CaseRetriever {
    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(HybridCaseRetriever.class);


    private final QdrantClient client;
    private final MultiModalEmbedder embedder;
    private final TenantGuard tenantGuard;
    private final RagConfig config;

    @Inject
    HybridCaseRetriever(QdrantClient client, MultiModalEmbedder embedder,
                        Instance<CurrentPrincipal> currentPrincipalInstance,
                        RagConfig config) {
        this(client,
            MatryoshkaMultiModalEmbedder.wrapIfNeeded(embedder, config.matryoshka().dimension()),
            TenantGuard.of(currentPrincipalInstance.isResolvable()
                ? currentPrincipalInstance.get() : null),
            config);
    }

    HybridCaseRetriever(QdrantClient client, MultiModalEmbedder embedder,
                        TenantGuard tenantGuard, RagConfig config) {
        this.client = client;
        this.embedder = embedder;
        this.tenantGuard = tenantGuard;
        this.config = config;
        if (config.retrieval().fusionStrategy() == FusionStrategy.DBSF) {
            double d = config.retrieval().weights().dense();
            double s = config.retrieval().weights().sparse();
            double b = config.retrieval().weights().bm25();
            if (Double.compare(d, s) != 0 || Double.compare(d, b) != 0) {
                LOG.warn("Non-equal fusion weights have no effect with DBSF strategy — " +
                         "DBSF uses server-side equal-weight fusion. Consider RRF or CC for per-leg weight control.");
            }
        }
    }

    @Override
    public List<RetrievedChunk> retrieve(RetrievalQuery query, CorpusRef corpus, int maxResults, PayloadFilter filter) {
        tenantGuard.assertTenant(corpus.tenantId());

        String collection = config.tenancyStrategy().collectionName(corpus);
        Optional<Filter> tenantFilter = config.tenancyStrategy().tenantFilter(corpus);
        Optional<Filter> payloadFilter = PayloadFilterTranslator.toQdrantFilter(filter);

        Filter.Builder combined = Filter.newBuilder();
        tenantFilter.ifPresent(tf -> combined.addAllMust(tf.getMustList()));
        payloadFilter.ifPresent(pf -> combined.addAllMust(pf.getMustList()));
        Optional<Filter> mergedFilter = combined.getMustCount() > 0
            ? Optional.of(combined.build()) : Optional.empty();

        // Check collection exists — return empty if not
        if (!collectionExists(collection)) {
            return List.of();
        }

        MultiModalEmbedding embedding = embedder.embedSeparate(query.searchText(), query.text());

        List<Float> denseVector = QdrantPointBuilder.floatListFrom(embedding.dense());

        boolean hasSparse = embedding.sparse() != null;
        boolean useFusion = hasSparse || config.bm25Enabled();
        FusionStrategy fusionStrategy = config.retrieval().fusionStrategy();

        // CC fusion uses client-side fusion, not server-side prefetch
        if (useFusion && fusionStrategy == FusionStrategy.CC) {
            return executeConvexCombinationFusion(collection, query, embedding,
                mergedFilter, maxResults);
        }

        // Weighted RRF: when weights are non-equal, use client-side fusion
        if (useFusion && fusionStrategy == FusionStrategy.RRF && !hasEqualActiveWeights(query)) {
            return executeRrfFusion(collection, query, embedding,
                mergedFilter, maxResults);
        }

        QueryPoints queryPoints;
        if (useFusion) {
            List<PrefetchQuery> prefetchLegs = new ArrayList<>();

            // Dense prefetch (always present in fusion mode)
            PrefetchQuery.Builder densePrefetch = PrefetchQuery.newBuilder()
                .setQuery(QueryFactory.nearest(denseVector))
                .setUsing(config.denseVectorName())
                .setLimit(config.retrieval().denseTopK());
            if (config.quantization().type() != DenseQuantization.NONE && config.quantization().oversampling().isPresent()) {
                densePrefetch.setParams(quantizationSearchParams());
            }
            mergedFilter.ifPresent(densePrefetch::setFilter);
            prefetchLegs.add(densePrefetch.build());

            // SPLADE prefetch (when available)
            if (hasSparse) {
                Map<Integer, Float> sparseMap = embedding.sparse();
                List<Float> sparseValues = new ArrayList<>(sparseMap.size());
                List<Integer> sparseIndices = new ArrayList<>(sparseMap.size());
                for (Map.Entry<Integer, Float> entry : sparseMap.entrySet()) {
                    sparseIndices.add(entry.getKey());
                    sparseValues.add(entry.getValue());
                }

                PrefetchQuery.Builder sparsePrefetch = PrefetchQuery.newBuilder()
                    .setQuery(QueryFactory.nearest(sparseValues, sparseIndices))
                    .setUsing(config.sparseVectorName())
                    .setLimit(config.retrieval().sparseTopK());
                mergedFilter.ifPresent(sparsePrefetch::setFilter);
                prefetchLegs.add(sparsePrefetch.build());
            }

            // BM25 prefetch (when enabled)
            if (config.bm25Enabled()) {
                String expandedQuery = CamelCaseExpander.expand(query.text());
                PrefetchQuery.Builder bm25Prefetch = PrefetchQuery.newBuilder()
                    .setQuery(QueryFactory.nearest(
                        Document.newBuilder()
                            .setText(expandedQuery)
                            .setModel(QdrantPointBuilder.BM25_MODEL)
                            .build()))
                    .setUsing(config.bm25VectorName())
                    .setLimit(config.retrieval().bm25TopK());
                mergedFilter.ifPresent(bm25Prefetch::setFilter);
                prefetchLegs.add(bm25Prefetch.build());
            }

            // ColBERT MAX_SIM two-stage: fusion as prefetch, ColBERT as outer query
            // Note: CC fusion does not support ColBERT reranking (handled separately above)
            if (embedder.supportedModes().contains(EmbeddingMode.COLBERT)
                    && embedding.colbert() != null
                    && config.retrieval().rerankEnabled()) {
                queryPoints = QueryPoints.newBuilder()
                    .setCollectionName(collection)
                    .addPrefetch(PrefetchQuery.newBuilder()
                        .setQuery(buildFusionQuery(fusionStrategy))
                        .setLimit(config.retrieval().rerankTopN())
                        .addAllPrefetch(prefetchLegs))
                    .setQuery(QueryFactory.nearest(embedding.colbert()))
                    .setUsing(config.colbertVectorName())
                    .setLimit(maxResults)
                    .setWithPayload(WithPayloadSelectorFactory.enable(true))
                    .build();
            } else {
                QueryPoints.Builder qb = QueryPoints.newBuilder()
                    .setCollectionName(collection);
                qb.addAllPrefetch(prefetchLegs);
                qb.setQuery(buildFusionQuery(fusionStrategy))
                   .setLimit(maxResults)
                   .setWithPayload(WithPayloadSelectorFactory.enable(true));
                queryPoints = qb.build();
            }
        } else {
            // Dense-only mode: direct nearest-neighbor query (no fusion)
            QueryPoints.Builder builder = QueryPoints.newBuilder()
                .setCollectionName(collection)
                .setQuery(QueryFactory.nearest(denseVector))
                .setUsing(config.denseVectorName())
                .setLimit(maxResults)
                .setWithPayload(WithPayloadSelectorFactory.enable(true));
            if (config.quantization().type() != DenseQuantization.NONE && config.quantization().oversampling().isPresent()) {
                builder.setParams(quantizationSearchParams());
            }
            mergedFilter.ifPresent(builder::setFilter);
            queryPoints = builder.build();
        }

        // Execute query and map to chunks
        List<ScoredPoint> scoredPoints = executeQuery(queryPoints);
        List<RetrievedChunk> chunks = mapToChunks(scoredPoints);

        // Sort by descending relevance and return
        chunks.sort((a, b) -> Double.compare(b.relevanceScore(), a.relevanceScore()));
        return Collections.unmodifiableList(chunks);
    }

    private boolean collectionExists(String collection) {
        try {
            return client.collectionExistsAsync(collection).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted checking collection existence", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to check collection existence", e.getCause());
        }
    }

    private List<ScoredPoint> executeQuery(QueryPoints queryPoints) {
        try {
            return client.queryAsync(queryPoints).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during query", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Query failed", e.getCause());
        }
    }

    private static String extractStringPayload(Map<String, Value> payload, String key) {
        Value value = payload.get(key);
        if (value != null && value.hasStringValue()) {
            return value.getStringValue();
        }
        return null;
    }

    private SearchParams quantizationSearchParams() {
        return SearchParams.newBuilder()
            .setQuantization(QuantizationSearchParams.newBuilder()
                .setOversampling(config.quantization().oversampling().getAsDouble())
                .setRescore(true)
                .build())
            .build();
    }

    private io.qdrant.client.grpc.Points.Query buildFusionQuery(FusionStrategy strategy) {
        return switch (strategy) {
            case RRF -> QueryFactory.rrf(Rrf.newBuilder().setK(config.retrieval().rrfK()).build());
            case DBSF -> QueryFactory.fusion(Fusion.DBSF);
            case CC -> throw new IllegalStateException(
                "CC fusion should be handled by executeConvexCombinationFusion");
        };
    }


    private double effectiveWeight(String leg, RetrievalQuery query) {
        double base = switch (leg) {
            case "dense" -> config.retrieval().weights().dense();
            case "sparse" -> config.retrieval().weights().sparse();
            case "bm25" -> config.retrieval().weights().bm25();
            case "quality" -> config.retrieval().weights().quality();
            default -> 1.0;
        };
        return base * query.weightMultipliers().getOrDefault(leg, 1.0);}

    private boolean hasEqualActiveWeights(RetrievalQuery query) {
        double dense = effectiveWeight("dense", query);
        double sparse = (embedder.supportedModes().contains(EmbeddingMode.SPARSE))
                        ? effectiveWeight("sparse", query) : dense;
        double bm25 = config.bm25Enabled() ? effectiveWeight("bm25", query) : dense;
        return Double.compare(dense, sparse) == 0 && Double.compare(dense, bm25) == 0;
    }

    private List<RetrievedChunk> executeRrfFusion(
            String collection, RetrievalQuery query, MultiModalEmbedding embedding,
            Optional<Filter> mergedFilter, int maxResults) {

        List<Float>                                 denseVector = QdrantPointBuilder.floatListFrom(embedding.dense());
        List<ScoreFusion.ScoredLeg<RetrievedChunk>> legs        = new ArrayList<>();

        QueryPoints.Builder denseQuery = QueryPoints.newBuilder()
                                                    .setCollectionName(collection)
                                                    .setQuery(QueryFactory.nearest(denseVector))
                                                    .setUsing(config.denseVectorName())
                                                    .setLimit(config.retrieval().denseTopK())
                                                    .setWithPayload(WithPayloadSelectorFactory.enable(true));
        if (config.quantization().type() != DenseQuantization.NONE && config.quantization().oversampling().isPresent()) {
            denseQuery.setParams(quantizationSearchParams());
        }
        mergedFilter.ifPresent(denseQuery::setFilter);

        List<ScoredPoint> densePoints = executeQuery(denseQuery.build());
        if (!densePoints.isEmpty()) {
            legs.add(new ScoreFusion.ScoredLeg<>(
                    mapToChunks(densePoints), RetrievedChunk::relevanceScore, effectiveWeight("dense", query)));
        }

        if (embedding.sparse() != null) {
            Map<Integer, Float> sparseMap     = embedding.sparse();
            List<Float>         sparseValues  = new ArrayList<>(sparseMap.size());
            List<Integer>       sparseIndices = new ArrayList<>(sparseMap.size());
            for (Map.Entry<Integer, Float> entry : sparseMap.entrySet()) {
                sparseIndices.add(entry.getKey());
                sparseValues.add(entry.getValue());
            }

            QueryPoints.Builder sparseQuery = QueryPoints.newBuilder()
                                                         .setCollectionName(collection)
                                                         .setQuery(QueryFactory.nearest(sparseValues, sparseIndices))
                                                         .setUsing(config.sparseVectorName())
                                                         .setLimit(config.retrieval().sparseTopK())
                                                         .setWithPayload(WithPayloadSelectorFactory.enable(true));
            mergedFilter.ifPresent(sparseQuery::setFilter);

            List<ScoredPoint> sparsePoints = executeQuery(sparseQuery.build());
            if (!sparsePoints.isEmpty()) {
                legs.add(new ScoreFusion.ScoredLeg<>(
                        mapToChunks(sparsePoints), RetrievedChunk::relevanceScore, effectiveWeight("sparse", query)));
            }
        }

        if (config.bm25Enabled()) {
            String expandedQuery = CamelCaseExpander.expand(query.text());
            QueryPoints.Builder bm25Query = QueryPoints.newBuilder()
                                                       .setCollectionName(collection)
                                                       .setQuery(QueryFactory.nearest(
                                                               Document.newBuilder()
                                                                       .setText(expandedQuery)
                                                                       .setModel(QdrantPointBuilder.BM25_MODEL)
                                                                       .build()))
                                                       .setUsing(config.bm25VectorName())
                                                       .setLimit(config.retrieval().bm25TopK())
                                                       .setWithPayload(WithPayloadSelectorFactory.enable(true));
            mergedFilter.ifPresent(bm25Query::setFilter);

            List<ScoredPoint> bm25Points = executeQuery(bm25Query.build());
            if (!bm25Points.isEmpty()) {
                legs.add(new ScoreFusion.ScoredLeg<>(
                        mapToChunks(bm25Points), RetrievedChunk::relevanceScore, effectiveWeight("bm25", query)));
            }
        }

        return ScoreFusion.rrf(legs, RetrievedChunk::fusionKey, maxResults, config.retrieval().rrfK())
                          .stream().map(f -> f.item().withRelevanceScore(f.score())).toList();
    }


    private List<RetrievedChunk> executeConvexCombinationFusion(
            String collection, RetrievalQuery query, MultiModalEmbedding embedding,
            Optional<Filter> mergedFilter, int maxResults) {

        List<Float> denseVector = QdrantPointBuilder.floatListFrom(embedding.dense());
        List<ScoreFusion.ScoredLeg<RetrievedChunk>> legs = new ArrayList<>();

        // Dense leg
        QueryPoints.Builder denseQuery = QueryPoints.newBuilder()
            .setCollectionName(collection)
            .setQuery(QueryFactory.nearest(denseVector))
            .setUsing(config.denseVectorName())
            .setLimit(config.retrieval().denseTopK())
            .setWithPayload(WithPayloadSelectorFactory.enable(true));
        if (config.quantization().type() != DenseQuantization.NONE && config.quantization().oversampling().isPresent()) {
            denseQuery.setParams(quantizationSearchParams());
        }
        mergedFilter.ifPresent(denseQuery::setFilter);

        List<ScoredPoint> densePoints = executeQuery(denseQuery.build());
        if (!densePoints.isEmpty()) {
            legs.add(new ScoreFusion.ScoredLeg<>(
                mapToChunks(densePoints), RetrievedChunk::relevanceScore, effectiveWeight("dense", query)));
        }

        // Sparse leg (if available)
        if (embedding.sparse() != null) {
            Map<Integer, Float> sparseMap = embedding.sparse();
            List<Float> sparseValues = new ArrayList<>(sparseMap.size());
            List<Integer> sparseIndices = new ArrayList<>(sparseMap.size());
            for (Map.Entry<Integer, Float> entry : sparseMap.entrySet()) {
                sparseIndices.add(entry.getKey());
                sparseValues.add(entry.getValue());
            }

            QueryPoints.Builder sparseQuery = QueryPoints.newBuilder()
                .setCollectionName(collection)
                .setQuery(QueryFactory.nearest(sparseValues, sparseIndices))
                .setUsing(config.sparseVectorName())
                .setLimit(config.retrieval().sparseTopK())
                .setWithPayload(WithPayloadSelectorFactory.enable(true));
            mergedFilter.ifPresent(sparseQuery::setFilter);

            List<ScoredPoint> sparsePoints = executeQuery(sparseQuery.build());
            if (!sparsePoints.isEmpty()) {
                legs.add(new ScoreFusion.ScoredLeg<>(
                    mapToChunks(sparsePoints), RetrievedChunk::relevanceScore, effectiveWeight("sparse", query)));
            }
        }

        // BM25 leg (if enabled)
        if (config.bm25Enabled()) {
            String expandedQuery = CamelCaseExpander.expand(query.text());
            QueryPoints.Builder bm25Query = QueryPoints.newBuilder()
                .setCollectionName(collection)
                .setQuery(QueryFactory.nearest(
                    Document.newBuilder()
                        .setText(expandedQuery)
                        .setModel(QdrantPointBuilder.BM25_MODEL)
                        .build()))
                .setUsing(config.bm25VectorName())
                .setLimit(config.retrieval().bm25TopK())
                .setWithPayload(WithPayloadSelectorFactory.enable(true));
            mergedFilter.ifPresent(bm25Query::setFilter);

            List<ScoredPoint> bm25Points = executeQuery(bm25Query.build());
            if (!bm25Points.isEmpty()) {
                legs.add(new ScoreFusion.ScoredLeg<>(
                    mapToChunks(bm25Points), RetrievedChunk::relevanceScore, effectiveWeight("bm25", query)));
            }
        }

        double qualityWeight = effectiveWeight("quality", query);
        Optional<String> qualityFieldOpt = config.retrieval().qualityPayloadField();
        if (qualityWeight > 0 && qualityFieldOpt.isPresent()) {
            String qualityField = qualityFieldOpt.get();
            double qualityMax = config.retrieval().qualityMax();

            java.util.Set<String> seen = new java.util.HashSet<>();
            List<RetrievedChunk> qualityItems = new ArrayList<>();
            for (var leg : legs) {
                for (var item : leg.items()) {
                    if (seen.add(item.fusionKey())) {
                        String rawValue = item.metadata().get(qualityField);
                        if (rawValue != null) {
                            try {
                                double val = Double.parseDouble(rawValue);
                                qualityItems.add(item.withRelevanceScore(Math.min(val / qualityMax, 1.0)));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
            if (!qualityItems.isEmpty()) {
                legs.add(new ScoreFusion.ScoredLeg<>(qualityItems, RetrievedChunk::relevanceScore, qualityWeight));
            }
        }

        return ScoreFusion.convexCombination(legs, RetrievedChunk::fusionKey, maxResults)
            .stream().map(f -> f.item().withRelevanceScore(f.score())).toList();
    }

    private List<RetrievedChunk> mapToChunks(List<ScoredPoint> scoredPoints) {
        List<RetrievedChunk> chunks = new ArrayList<>(scoredPoints.size());
        for (ScoredPoint point : scoredPoints) {
            Map<String, Value> payload = point.getPayloadMap();

            String content = extractStringPayload(payload, "content");
            String sourceDocumentId = extractStringPayload(payload, "sourceDocumentId");

            if (content == null || sourceDocumentId == null) {
                continue; // skip malformed points
            }

            // Extract remaining payload entries as metadata (excluding reserved keys)
            Map<String, String> metadata = new HashMap<>();
            for (Map.Entry<String, Value> entry : payload.entrySet()) {
                if (!QdrantPointBuilder.RESERVED_PAYLOAD_KEYS.contains(entry.getKey())) {
                    Value v = entry.getValue();
                    if (v.hasStringValue()) {
                        metadata.put(entry.getKey(), v.getStringValue());
                    } else if (v.hasDoubleValue()) {
                        metadata.put(entry.getKey(), String.valueOf(v.getDoubleValue()));
                    } else if (v.hasIntegerValue()) {
                        metadata.put(entry.getKey(), String.valueOf(v.getIntegerValue()));
                    }
                }
            }

            chunks.add(new RetrievedChunk(content, sourceDocumentId,
                point.getScore(), Map.copyOf(metadata)));
        }
        return chunks;
    }
}
