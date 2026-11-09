package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.EmbeddingIngestor;

import java.util.logging.Level;
import java.util.logging.Logger;

public class CorpusReindexService {

    private static final Logger LOG = Logger.getLogger(CorpusReindexService.class.getName());

    private final EmbeddingIngestor embeddingIngestor;
    private final CorpusRef corpusRef;
    private final ResetAction resetAction;

    @FunctionalInterface
    public interface ResetAction {
        void reset(CorpusRef corpusRef, String corpusId);
    }

    public CorpusReindexService(EmbeddingIngestor embeddingIngestor, CorpusRef corpusRef,
                                ResetAction resetAction) {
        this.embeddingIngestor = embeddingIngestor;
        this.corpusRef = corpusRef;
        this.resetAction = resetAction;
    }

    public ReindexResult reindex() {
        int fileCount;
        try {
            fileCount = embeddingIngestor.listDocuments(corpusRef).size();
        } catch (Exception e) {
            fileCount = -1;
        }

        try {
            resetAction.reset(corpusRef, corpusRef.corpusName());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to trigger reindex", e);
            return new ReindexResult("error",
                    "Reindex failed for corpus '" + corpusRef.corpusName() + "': " + e.getMessage());
        }

        String message = "Reindex triggered for corpus '" + corpusRef.corpusName()
                + "'. Collection deleted, cursor reset. Re-embedding will complete on next ingestion cycle"
                + (fileCount >= 0 ? " (" + fileCount + " entries in corpus)." : ".");
        return new ReindexResult("ok", message);
    }

    public record ReindexResult(String status, String message) {}
}
