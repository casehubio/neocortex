package io.casehub.rag.runtime;

import io.casehub.rag.ChunkInput;
import io.casehub.rag.CorpusRef;
import io.casehub.rag.CorpusStore;
import io.casehub.rag.ReactiveCorpusStore;
import io.quarkus.arc.DefaultBean;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@DefaultBean
@ApplicationScoped
public class BlockingToReactiveCorpusStore implements ReactiveCorpusStore {

    @Inject CorpusStore delegate;

    public BlockingToReactiveCorpusStore() {}

    public BlockingToReactiveCorpusStore(CorpusStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public Uni<Void> ingest(CorpusRef corpus, List<ChunkInput> chunks) {
        return Uni.createFrom().<Void>item(() -> { delegate.ingest(corpus, chunks); return null; })
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Void> deleteDocument(CorpusRef corpus, String sourceDocumentId) {
        return Uni.createFrom().<Void>item(() -> { delegate.deleteDocument(corpus, sourceDocumentId); return null; })
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Void> deleteCorpus(CorpusRef corpus) {
        return Uni.createFrom().<Void>item(() -> { delegate.deleteCorpus(corpus); return null; })
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<List<String>> listDocuments(CorpusRef corpus) {
        return Uni.createFrom().item(() -> delegate.listDocuments(corpus))
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}
