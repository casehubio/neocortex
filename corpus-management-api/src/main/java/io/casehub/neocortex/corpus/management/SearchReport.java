package io.casehub.neocortex.corpus.management;

import io.casehub.neocortex.rag.CorpusRef;

import java.util.List;

public record SearchReport(
    List<CorpusRef> searched,
    List<SkippedCorpus> skipped,
    boolean partial
) {}
