package io.casehub.neocortex.rag.cdi;

import io.casehub.neocortex.rag.CursorStore;
import io.casehub.neocortex.rag.EmbeddingIngestor;
import io.casehub.neocortex.rag.MetadataExtractor;
import io.casehub.neocortex.rag.runtime.BM25IndexRegistry;
import io.casehub.neocortex.rag.runtime.CorpusIngestionBinding;
import io.casehub.neocortex.rag.runtime.CorpusIngestionService;
import io.casehub.neocortex.rag.runtime.FileCursorStore;
import io.casehub.neocortex.rag.runtime.HybridCaseRetriever;
import io.casehub.neocortex.rag.runtime.IngestionConfig;
import io.casehub.neocortex.rag.runtime.RagConfig;
import io.casehub.neocortex.rag.runtime.TenantGuard;
import io.casehub.neocortex.rag.runtime.YamlFrontmatterExtractor;
import io.casehub.neocortex.rag.runtime.CorpusBindingProducer;
import io.casehub.neocortex.inference.MatryoshkaMultiModalEmbedder;
import io.casehub.neocortex.inference.MultiModalEmbedder;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.qdrant.client.QdrantClient;
import io.quarkus.arc.DefaultBean;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class RagCdiBeans {

    @Produces
    @ApplicationScoped
    public BM25IndexRegistry bm25IndexRegistry(RagConfig ragConfig) {
        return new BM25IndexRegistry(ragConfig.tenancyStrategy());
    }

    @Produces
    @ApplicationScoped
    public HybridCaseRetriever hybridCaseRetriever(QdrantClient client,
                                                    MultiModalEmbedder embedder,
                                                    Instance<CurrentPrincipal> currentPrincipalInstance,
                                                    RagConfig config) {
        return new HybridCaseRetriever(client,
            MatryoshkaMultiModalEmbedder.wrapIfNeeded(embedder, config.matryoshka().dimension()),
            TenantGuard.of(currentPrincipalInstance.isResolvable()
                ? currentPrincipalInstance.get() : null),
            config);
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public CursorStore fileCursorStore(IngestionConfig config) {
        return new FileCursorStore(config.cursorDir());
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public MetadataExtractor yamlFrontmatterExtractor() {
        return new YamlFrontmatterExtractor();
    }

    @Produces
    @ApplicationScoped
    public CorpusIngestionService corpusIngestionService(EmbeddingIngestor ingestor,
                                                         CursorStore cursorStore) {
        return new CorpusIngestionService(ingestor, cursorStore);
    }

    // --- Lifecycle wiring ---

    @Inject CorpusIngestionService ingestionService;
    @Inject CorpusBindingProducer bindingProducer;
    @Inject Instance<CorpusIngestionBinding> customBindings;
    @Inject IngestionConfig config;

    void onStart(@Observes StartupEvent event) {
        ingestionService.startAutoBindings(allBindings(), config);
    }

    @PreDestroy
    void shutdown() {
        ingestionService.shutdown();
    }

    @Scheduled(every = "${casehub.rag.ingestion.interval:30s}",
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void poll() {
        ingestionService.poll(allBindings(), config);
    }

    @Scheduled(every = "${casehub.rag.ingestion.cursor-checkpoint-interval:5m}",
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void checkpointCursors() {
        ingestionService.checkpointCursors(allBindings());
    }

    private List<CorpusIngestionBinding> allBindings() {
        List<CorpusIngestionBinding> all = new ArrayList<>();
        if (bindingProducer != null) {
            all.addAll(bindingProducer.bindings());
        }
        if (customBindings != null) {
            customBindings.forEach(all::add);
        }
        return all;
    }
}
