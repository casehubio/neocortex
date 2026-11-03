package io.casehub.neocortex.corpus.management;

import io.casehub.neocortex.rag.CorpusRef;

public record SkippedCorpus(CorpusRef corpus, String reason) {}
