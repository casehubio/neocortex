package io.casehub.neocortex.rag.runtime;

import io.casehub.neocortex.inference.MultiModalEmbedder;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.CursorStore;
import io.casehub.neocortex.rag.EmbeddingIngestor;
import io.qdrant.client.QdrantClient;

import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CorpusCollectionMigration {

    private static final Logger LOG = Logger.getLogger(CorpusCollectionMigration.class.getName());

    private final MultiModalEmbedder embedder;
    private final QdrantClient qdrantClient;
    private final EmbeddingIngestor embeddingIngestor;
    private final CursorStore cursorStore;
    private final String corpusId;
    private final String corpusPrefix;
    private final RagConfig ragConfig;

    public CorpusCollectionMigration(MultiModalEmbedder embedder, QdrantClient qdrantClient,
                                     EmbeddingIngestor embeddingIngestor, CursorStore cursorStore,
                                     String corpusId, String corpusPrefix, RagConfig ragConfig) {
        this.embedder = embedder;
        this.qdrantClient = qdrantClient;
        this.embeddingIngestor = embeddingIngestor;
        this.cursorStore = cursorStore;
        this.corpusId = corpusId;
        this.corpusPrefix = corpusPrefix;
        this.ragConfig = ragConfig;
    }

    public void resetCorpus(CorpusRef corpusRef) {
        embeddingIngestor.deleteCorpus(corpusRef);
        cursorStore.save(corpusId, "");
    }

    public void checkAndMigrate(int maxRetries, long retryDelayMs) {
        validateColbertLimit();

        if (!waitForQdrant(maxRetries, retryDelayMs)) {
            return;
        }

        CorpusRef corpusRef = new CorpusRef(corpusPrefix, corpusId);
        String collectionName = ragConfig.tenancyStrategy().collectionName(corpusRef);

        try {
            if (!qdrantClient.collectionExistsAsync(collectionName).get()) {
                if (cursorStore.load(corpusId).isPresent()) {
                    LOG.log(Level.INFO,
                            "Collection ''{0}'' does not exist but cursor found — clearing cursor to force re-indexing",
                            collectionName);
                    cursorStore.save(corpusId, "");
                }
                return;
            }

            var expected = new CollectionExpectedConfig(
                    embedder.denseDimension(),
                    true,
                    embedder.colbertDimension().isPresent());
            MigrationAction action = CollectionCompatibility.check(qdrantClient, collectionName, expected);

            switch (action) {
                case MigrationAction.Compatible c ->
                    LOG.log(Level.INFO, "Collection ''{0}'' is up-to-date — no migration needed",
                            collectionName);
                case MigrationAction.DimensionMismatch m -> {
                    LOG.log(Level.INFO,
                            "Collection ''{0}'' dense dimension {1} != expected {2} — re-indexing",
                            new Object[]{collectionName, m.actual(), m.expected()});
                    resetCorpus(corpusRef);
                }
                case MigrationAction.MissingSparseVectors sv -> {
                    LOG.log(Level.INFO, "Collection ''{0}'' lacks sparse vectors — re-indexing",
                            collectionName);
                    resetCorpus(corpusRef);
                }
                case MigrationAction.MissingColBert cb -> {
                    LOG.log(Level.INFO,
                            "Collection ''{0}'' lacks ColBERT multi-vector config — re-indexing",
                            collectionName);
                    resetCorpus(corpusRef);
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(Level.WARNING, "Interrupted during collection migration check", e);
        } catch (ExecutionException e) {
            LOG.log(Level.WARNING, "Failed to check collection for migration", e.getCause());
        }
    }

    private boolean waitForQdrant(int maxRetries, long delayMs) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                qdrantClient.listCollectionsAsync().get();
                LOG.log(Level.INFO, "Qdrant ready (attempt {0}/{1})",
                        new Object[]{attempt, maxRetries});
                return true;
            } catch (ExecutionException e) {
                LOG.log(Level.WARNING, "Qdrant not ready (attempt {0}/{1}): {2}",
                        new Object[]{attempt, maxRetries, e.getCause().getMessage()});
                if (attempt < maxRetries && delayMs > 0) {
                    try { Thread.sleep(delayMs); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        LOG.warning("Qdrant readiness check exhausted — proceeding without migration");
        return false;
    }

    private void validateColbertLimit() {
        if (embedder.colbertDimension().isEmpty()) {
            return;
        }
        int colbertDim = embedder.colbertDimension().getAsInt();
        long totalFloats = (long) embedder.maxSequenceLength() * colbertDim;
        int limit = ragConfig.maxMultivectorFloats();
        if (totalFloats > limit) {
            throw new IllegalStateException(
                    "ColBERT multi-vector size exceeds limit: maxSequenceLength=%d × colbertDimension=%d = %d floats > maxMultivectorFloats=%d. Reduce max-sequence-length or raise casehub.rag.max-multivector-floats."
                            .formatted(embedder.maxSequenceLength(), colbertDim, totalFloats, limit));
        }
    }
}
