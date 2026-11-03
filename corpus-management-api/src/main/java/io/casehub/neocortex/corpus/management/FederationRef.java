package io.casehub.neocortex.corpus.management;

import io.casehub.neocortex.rag.CorpusRef;

public record FederationRef(
    CorpusRef localRef,
    String url,
    String idPrefix,
    String searchOrder
) {}
