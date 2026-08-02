package io.casehub.neocortex.rag.runtime;

import io.casehub.neocortex.inference.MultiModalEmbedder;
import io.casehub.neocortex.inference.MultiModalEmbedding;
import io.casehub.neocortex.rag.ChunkInput;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.EmbeddingIngestor;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QueryFactory;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.grpc.Points.QueryPoints;
import io.qdrant.client.grpc.Points.ScoredPoint;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Decorator
@Priority(50)
public class DedupEmbeddingIngestor implements EmbeddingIngestor {

    private static final Logger LOG = Logger.getLogger(DedupEmbeddingIngestor.class.getName());

    private final EmbeddingIngestor delegate;
    private final MultiModalEmbedder embedder;
    private final QdrantClient qdrantClient;
    private final DedupIngestionConfig config;
    private final RagConfig ragConfig;

    @Inject
    DedupEmbeddingIngestor(@Delegate @Any EmbeddingIngestor delegate,
                           MultiModalEmbedder embedder,
                           QdrantClient qdrantClient,
                           DedupIngestionConfig config,
                           RagConfig ragConfig) {
        this.delegate = delegate;
        this.embedder = embedder;
        this.qdrantClient = qdrantClient;
        this.config = config;
        this.ragConfig = ragConfig;
    }

    @Override
    public void ingest(CorpusRef corpus, List<ChunkInput> chunks) {
        if (!config.enabled() || chunks.isEmpty()) {
            delegate.ingest(corpus, chunks);
            return;
        }

        String collection = ragConfig.tenancyStrategy().collectionName(corpus);
        List<ChunkInput> unique = new ArrayList<>(chunks.size());

        for (ChunkInput chunk : chunks) {
            if (isDuplicate(collection, chunk)) {
                LOG.info(() -> "Dedup: skipping near-duplicate '" + chunk.sourceDocumentId()
                        + "' (cosine > " + config.threshold() + ")");
            } else {
                unique.add(chunk);
            }
        }

        if (!unique.isEmpty()) {
            delegate.ingest(corpus, unique);
        }
    }

    private boolean isDuplicate(String collection, ChunkInput chunk) {
        try {
            MultiModalEmbedding embedding = embedder.embed(chunk.content());
            List<Float> denseVector = QdrantPointBuilder.floatListFrom(embedding.dense());

            QueryPoints query = QueryPoints.newBuilder()
                    .setCollectionName(collection)
                    .setQuery(QueryFactory.nearest(denseVector))
                    .setUsing(ragConfig.denseVectorName())
                    .setLimit(1)
                    .setWithPayload(WithPayloadSelectorFactory.enable(false))
                    .build();

            List<ScoredPoint> results = qdrantClient.queryAsync(query).get();
            if (!results.isEmpty() && results.getFirst().getScore() >= config.threshold()) {
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(Level.WARNING, "Dedup check interrupted for " + chunk.sourceDocumentId(), e);
        } catch (ExecutionException e) {
            LOG.log(Level.WARNING, "Dedup check failed for " + chunk.sourceDocumentId()
                    + " — proceeding with ingestion", e.getCause());
        }
        return false;
    }

    @Override
    public void deleteDocument(CorpusRef corpus, String sourceDocumentId) {
        delegate.deleteDocument(corpus, sourceDocumentId);
    }

    @Override
    public void deleteCorpus(CorpusRef corpus) {
        delegate.deleteCorpus(corpus);
    }

    @Override
    public List<String> listDocuments(CorpusRef corpus) {
        return delegate.listDocuments(corpus);
    }
}
