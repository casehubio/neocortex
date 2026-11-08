package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.CorpusReader;
import io.casehub.neocortex.corpus.VersionInfo;
import io.casehub.neocortex.rag.ExtractionResult;
import io.casehub.neocortex.rag.MetadataExtractor;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class EntrySummariser {

    private final CorpusReader reader;
    private final MetadataExtractor extractor;

    public EntrySummariser(CorpusReader reader, MetadataExtractor extractor) {
        this.reader = Objects.requireNonNull(reader);
        this.extractor = Objects.requireNonNull(extractor);
    }

    public EntrySummary summarise(String path) {
        List<VersionInfo> versions = reader.versions(path);
        int versionCount = versions.size();
        Instant lastModified = versions.isEmpty() ? null
                : versions.get(versions.size() - 1).timestamp();

        String title = null;
        Optional<byte[]> content = reader.read(path);
        if (content.isPresent()) {
            ExtractionResult extracted = extractor.extract(path, content.get());
            title = extracted.metadata().get("title");
        }

        return new EntrySummary(path, title, lastModified, versionCount);
    }
}
