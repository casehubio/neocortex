package io.casehub.neocortex.examples.corpus;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.impl.ChainWalkerFederatedSearch;
import io.casehub.neocortex.corpus.management.impl.SqliteCorpusCatalogue;
import io.casehub.neocortex.corpus.management.impl.SqliteCorpusEntryStore;
import io.casehub.neocortex.rag.ChunkInput;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.EmbeddingIngestor;
import io.casehub.neocortex.sqlite.SqliteDataSourceFactory;

import java.util.List;

public class CorpusTestHarness {

    private final SqliteCorpusCatalogue catalogue;
    private final SqliteCorpusEntryStore entryStore;
    private final InMemoryCorpusSearch search;
    private final ChainWalkerFederatedSearch federatedSearch;

    private CorpusTestHarness(SqliteCorpusCatalogue catalogue,
                               SqliteCorpusEntryStore entryStore,
                               InMemoryCorpusSearch search,
                               ChainWalkerFederatedSearch federatedSearch) {
        this.catalogue = catalogue;
        this.entryStore = entryStore;
        this.search = search;
        this.federatedSearch = federatedSearch;
    }

    public static CorpusTestHarness create() {
        var ds = SqliteDataSourceFactory.create(":memory:", 1, 3000);
        var catalogue = new SqliteCorpusCatalogue(ds);
        var noOpIngestor = new NoOpIngestor();
        var entryStore = new SqliteCorpusEntryStore(ds, noOpIngestor);
        var search = new InMemoryCorpusSearch();
        var federatedSearch = new ChainWalkerFederatedSearch(catalogue, search, 0.1);
        return new CorpusTestHarness(catalogue, entryStore, search, federatedSearch);
    }

    public SqliteCorpusCatalogue catalogue() { return catalogue; }
    public SqliteCorpusEntryStore entryStore() { return entryStore; }
    public InMemoryCorpusSearch search() { return search; }
    public ChainWalkerFederatedSearch federatedSearch() { return federatedSearch; }

    public void addEntry(CorpusRef corpus, CorpusEntry entry) {
        entryStore.add(corpus, entry);
        search.index(corpus, entry);
    }

    private static class NoOpIngestor implements EmbeddingIngestor {
        @Override public void ingest(CorpusRef corpus, List<ChunkInput> chunks) {}
        @Override public void deleteDocument(CorpusRef corpus, String sourceDocumentId) {}
        @Override public void deleteCorpus(CorpusRef corpus) {}
        @Override public List<String> listDocuments(CorpusRef corpus) { return List.of(); }
    }
}
