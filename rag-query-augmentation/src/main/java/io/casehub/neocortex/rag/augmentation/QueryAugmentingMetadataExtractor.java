package io.casehub.neocortex.rag.augmentation;

import io.casehub.neocortex.rag.DocumentQueryAugmenter;
import io.casehub.neocortex.rag.ExtractionResult;
import io.casehub.neocortex.rag.MetadataExtractor;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

@Decorator
@Priority(50)
public class QueryAugmentingMetadataExtractor implements MetadataExtractor {

    @Inject @Delegate
    MetadataExtractor delegate;

    @Inject
    DocumentQueryAugmenter augmenter;

    @Override
    public ExtractionResult extract(String path, byte[] content) {
        ExtractionResult base = delegate.extract(path, content);

        String title = base.metadata().getOrDefault("title", "");
        String body = new String(content, java.nio.charset.StandardCharsets.UTF_8);

        Optional<List<String>> queries = augmenter.generateQueries(title, body, path);
        if (queries.isEmpty() || queries.get().isEmpty()) return base;

        String augmentedBody = base.body() + "\n\n" +
            String.join("\n", queries.get());

        return new ExtractionResult(augmentedBody, base.metadata(), base.listMetadata());
    }
}
