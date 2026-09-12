package io.casehub.neocortex.rag.cdi;

import io.casehub.neocortex.inference.MultiModalEmbedder;
import io.casehub.neocortex.rag.EmbeddingIngestor;
import io.casehub.neocortex.rag.runtime.DedupEmbeddingIngestor;
import io.casehub.neocortex.rag.runtime.DedupIngestionConfig;
import io.casehub.neocortex.rag.runtime.RagConfig;
import io.qdrant.client.QdrantClient;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

@Decorator
@Priority(50)
public class DedupDecorator extends DedupEmbeddingIngestor {

    @Inject
    DedupDecorator(@Delegate @Any EmbeddingIngestor delegate,
                   MultiModalEmbedder embedder,
                   QdrantClient qdrantClient,
                   DedupIngestionConfig config,
                   RagConfig ragConfig) {
        super(delegate, embedder, qdrantClient, config, ragConfig);
    }
}
