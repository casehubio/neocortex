package io.casehub.neocortex.corpus.management;

import java.util.List;

public record FederatedSearchResult(
    List<CorpusSearchResult> results,
    SearchReport report
) {}
