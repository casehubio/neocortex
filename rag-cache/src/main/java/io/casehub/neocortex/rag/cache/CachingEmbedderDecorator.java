package io.casehub.neocortex.rag.cache;

import io.casehub.neocortex.inference.EmbeddingMode;
import io.casehub.neocortex.inference.MultiModalEmbedder;
import io.casehub.neocortex.inference.MultiModalEmbedding;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import java.io.File;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Decorator
@Priority(100)
public class CachingEmbedderDecorator implements MultiModalEmbedder {

    private static final Logger LOG =
            Logger.getLogger(CachingEmbedderDecorator.class.getName());

    @Inject @Delegate @Any
    MultiModalEmbedder delegate;

    @Inject
    EmbeddingCacheConfig config;

    private CachingMultiModalEmbedder caching;
    private EmbeddingCache cache;

    @PostConstruct
    void init() {
        if (!config.enabled() || config.path().isBlank()) {
            LOG.info("Embedding cache disabled");
            return;
        }

        try {
            File dbFile = new File(config.path());
            File parent = dbFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            String modelVersion = delegate.denseDimension()
                    + ":" + delegate.maxSequenceLength()
                    + ":" + config.versionSuffix();
            cache = new EmbeddingCache(config.path(), modelVersion);
            cache.init();
            caching = new CachingMultiModalEmbedder(delegate, cache, true);
            LOG.info(() -> "Embedding cache enabled at " + config.path()
                    + " (model version: " + modelVersion + ")");
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Embedding cache init failed — proceeding without cache", e);
            caching = null;
        }
    }

    @PreDestroy
    void shutdown() {
        if (cache != null) cache.shutdown();
    }

    @Override
    public MultiModalEmbedding embed(String text) {
        return active() ? caching.embed(text) : delegate.embed(text);
    }

    @Override
    public List<MultiModalEmbedding> embedBatch(List<String> texts) {
        return active() ? caching.embedBatch(texts) : delegate.embedBatch(texts);
    }

    @Override
    public MultiModalEmbedding embedSeparate(String denseText, String nonDenseText) {
        return active()
                ? caching.embedSeparate(denseText, nonDenseText)
                : delegate.embedSeparate(denseText, nonDenseText);
    }

    @Override
    public Set<EmbeddingMode> supportedModes() {
        return delegate.supportedModes();
    }

    @Override
    public int denseDimension() {
        return delegate.denseDimension();
    }

    @Override
    public OptionalInt colbertDimension() {
        return delegate.colbertDimension();
    }

    @Override
    public int maxSequenceLength() {
        return delegate.maxSequenceLength();
    }

    private boolean active() {
        return caching != null;
    }
}
