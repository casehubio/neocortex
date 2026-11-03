package io.casehub.neocortex.corpus.management;

import io.casehub.neocortex.rag.CorpusRef;

import java.time.Instant;

public record CorpusSearchResult(
    CorpusEntry entry,
    double score,
    CorpusRef sourceCorpus,
    int tier,
    Instant entryTimestamp
) {}
